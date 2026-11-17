#pragma once
// What the title is doing, as far as the app can tell: in its menus, or in a
// match.
//
// The app uses it to show the controls that belong to each -- a d-pad over the
// menus, and nothing extra over the pitch.
//
// The signal is the title's own state machine. Its procedure switch
// (sub_82F00CE8, hooked in pes_hooks.cpp) is handed the name of the screen it is
// about to run, and those are the names the tools already wait on: the main menu
// is `TopMenu/TopMenu`, the menus below it are `Exhibition/*`, `LiveData/*` and
// the `Cmn*` checks, and a match is `Match/*` (`MatchSetup`, `MatchIdle`, ...).
// A name that is not one of them says nothing -- a load step, a report -- and
// leaves the last answer standing.
//
// An in-match menu (the pause menu) runs inside the match's own procedure, so
// it reads as a match; giving it a phase of its own needs a name that tells it
// apart, which game.log's `procedure:` lines would show.
//
// A build whose title never names a procedure has the player entity's existence
// as its fallback (player::NowRunning), read from the guest.
//
// Header-only and free of the guest, so tests/ checks the transitions on a tree
// with no game image.
#include <atomic>
#include <cstdint>
#include <cstring>

namespace phase
{
    enum class Phase : std::uint8_t
    {
        Unknown = 0,   // nothing to read: the app keeps its own layout rules
        Menus = 1,
        Match = 2,
    };

    // What a procedure name says about the phase, or Unknown when it says
    // nothing. The names are the title's own, matched by prefix so every step of
    // a flow is covered (`Match/` covers `MatchSetup` and the rest). A screen
    // that reports a name not listed here is one line away from being covered.
    inline Phase Classify(const char* procedureName)
    {
        if (!procedureName || !*procedureName) return Phase::Unknown;
        struct Rule { const char* prefix; Phase phase; };
        static const Rule kRules[] = {
            { "Match/",               Phase::Match },
            { "TopMenu/",             Phase::Menus },   // the main menu
            { "Intro/",               Phase::Menus },   // first run: storage, language, data packs
            { "FirstFlow/",           Phase::Menus },
            { "PreStartup",           Phase::Menus },   // logos, then the title screen
            { "SystemDataLoad",       Phase::Menus },
            { "MainMenuSelector",     Phase::Menus },
            { "LiveData/",            Phase::Menus },
            { "Exhibition/",          Phase::Menus },   // team select, pre-match
            { "CmnReportEventCheck",  Phase::Menus },   // the Football Life report
            { "CmnCoopSettingsCheck", Phase::Menus },
        };
        for (const Rule& rule : kRules)
            if (std::strncmp(procedureName, rule.prefix, std::strlen(rule.prefix)) == 0)
                return rule.phase;
        return Phase::Unknown;
    }

    // The last phase a named procedure implied. Written by the procedure hook on
    // the guest's own thread (pes_hooks.cpp) and read from the input poll.
    inline std::atomic<std::uint8_t>& Told()
    {
        static std::atomic<std::uint8_t> told{ std::uint8_t(Phase::Unknown) };
        return told;
    }

    // Note a procedure the title is about to run, and answer what it said. A
    // name that means nothing leaves the answer where it was, so a load step in
    // the middle of a match is not read as a menu.
    inline Phase NoteProcedure(const char* procedureName)
    {
        const Phase named = Classify(procedureName);
        if (named != Phase::Unknown) Told().store(std::uint8_t(named), std::memory_order_relaxed);
        return named;
    }

    inline Phase ToldPhase()
    {
        return Phase(Told().load(std::memory_order_relaxed));
    }

    class Tracker
    {
    public:
        // Once every half second: a menu reached from a match takes seconds to
        // arrive, and a read of the guest's memory is not free.
        static constexpr double kSampleSeconds = 0.5;

        // Whether a read is due. Call this first, so the guest is only read when
        // the answer can change.
        bool Due(double nowSeconds) const
        {
            return !sampled_ || nowSeconds - sampledAt_ >= kSampleSeconds;
        }

        // A phase another part of the runtime already knows (the title's named
        // procedures), sampled the same way.
        Phase Observe(Phase told, double nowSeconds)
        {
            if (!Due(nowSeconds)) return current_;
            sampled_ = true;
            sampledAt_ = nowSeconds;
            current_ = told;
            return current_;
        }

        // The fallback, for a build whose title names nothing: `known` is false
        // when the build has no addresses to read, which is not the same as "no
        // player": one keeps the app's own layout, the other says the title is
        // in its menus.
        Phase Observe(bool known, bool playerExists, double nowSeconds)
        {
            if (!Due(nowSeconds)) return current_;
            sampled_ = true;
            sampledAt_ = nowSeconds;
            current_ = !known ? Phase::Unknown
                     : playerExists ? Phase::Match
                     : Phase::Menus;
            return current_;
        }

        Phase Current() const { return current_; }

    private:
        Phase current_ = Phase::Unknown;   // until the first read
        double sampledAt_ = 0.0;
        bool sampled_ = false;
    };
}
