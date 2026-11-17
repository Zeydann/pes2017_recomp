// The menu/match phase the app's d-pad follows (runtime/game_phase.h): sampled,
// not asked every frame, and honest about a build that has nothing to read.
#include "game_phase.h"

#include <cstdio>

namespace
{
    int g_checks = 0;
    int g_failures = 0;

    void Check(bool ok, const char* what)
    {
        g_checks++;
        if (ok) return;
        g_failures++;
        std::fprintf(stdout, "FAIL: %s\n", what);
    }

    using phase::Phase;
}

int main()
{
    {
        phase::Tracker tracker;
        Check(tracker.Current() == Phase::Unknown, "nothing is known before the first read");
        Check(tracker.Due(0.0), "and the first read is due at once");
    }

    {
        phase::Tracker tracker;
        Check(tracker.Observe(true, false, 0.0) == Phase::Menus, "no player yet means the menus");
        Check(tracker.Observe(true, true, 0.1) == Phase::Menus, "a sample inside the window is not taken");
        Check(tracker.Observe(true, true, 0.5) == Phase::Match, "a player means a match");
        Check(tracker.Current() == Phase::Match, "and the last answer is kept");
    }

    {
        // The sample window is what keeps the guest read off the per-frame path.
        phase::Tracker tracker;
        tracker.Observe(true, true, 10.0);
        Check(!tracker.Due(10.2), "nothing is due inside the window");
        Check(tracker.Due(10.5), "and one is due when it has passed");
        Check(tracker.Observe(true, false, 10.5) == Phase::Menus, "back to the menus");
        Check(tracker.Observe(true, true, 11.0) == Phase::Match, "and into the next match");
    }

    {
        // A build with no addresses for the title's client states has nothing to
        // read, so it must not claim the menus: the app would then hide the
        // d-pad on a pitch it cannot recognise.
        phase::Tracker tracker;
        Check(tracker.Observe(false, false, 0.0) == Phase::Unknown, "no source, no phase");
        Check(tracker.Observe(false, true, 1.0) == Phase::Unknown, "still unknown whatever a read would say");
    }

    {
        // The title's own procedure names are the signal the app follows:
        // `TopMenu/TopMenu` is the main menu, `Match/*` is a match, and the
        // screens reached from the menus are menus.
        Check(phase::Classify("TopMenu/TopMenu") == Phase::Menus, "TopMenu is the main menu");
        Check(phase::Classify("Exhibition/ExhibiFlowInit") == Phase::Menus, "team select is a menu");
        Check(phase::Classify("CmnCoopSettingsCheck") == Phase::Menus, "a settings check is a menu");
        Check(phase::Classify("Match/MatchSetup") == Phase::Match, "kick-off is a match");
        Check(phase::Classify("Match/MatchIdle") == Phase::Match, "and every later step of it");
        Check(phase::Classify("Match/MatchPostPause") == Phase::Match, "including the ones after a pause");
    }

    {
        // A load step or a report says nothing about the phase, so it must not
        // move it: a match that loads something is still a match.
        Check(phase::Classify("CmnLoadIdle") == Phase::Unknown, "a load step names no phase");
        Check(phase::Classify("") == Phase::Unknown, "and neither does an empty name");
        Check(phase::Classify(nullptr) == Phase::Unknown, "nor a missing one");

        Check(phase::NoteProcedure("TopMenu/TopMenu") == Phase::Menus, "the main menu is noted");
        Check(phase::ToldPhase() == Phase::Menus, "and is what the poll reads");
        Check(phase::NoteProcedure("CmnLoadIdle") == Phase::Unknown, "a load step in the menus says nothing");
        Check(phase::ToldPhase() == Phase::Menus, "which leaves the menus standing");
        phase::NoteProcedure("Match/MatchSetup");
        Check(phase::ToldPhase() == Phase::Match, "kick-off moves it to the match");
        phase::NoteProcedure("CmnLoadIdle");
        Check(phase::ToldPhase() == Phase::Match, "and a load inside the match keeps it there");
    }

    {
        // What the procedures said is sampled like the guest read: twice in one
        // window is one report to the app.
        phase::Tracker tracker;
        Check(tracker.Observe(Phase::Match, 20.0) == Phase::Match, "a told phase is reported at once");
        Check(tracker.Observe(Phase::Menus, 20.1) == Phase::Match, "a sample inside the window is not taken");
        Check(tracker.Observe(Phase::Menus, 20.5) == Phase::Menus, "and the next one after it is");
    }

    std::printf("%d checks, %d failed\n", g_checks, g_failures);
    return g_failures == 0 ? 0 : 1;
}
