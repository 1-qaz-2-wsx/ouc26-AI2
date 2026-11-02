#include "playback.hpp"
#include <iostream>
#include <stdexcept>
#include <cmath>

static int checks = 0;
static void check(bool condition, const char* message) {
    ++checks;
    if (!condition) throw std::runtime_error(message);
}
int main() {
    try {
        np::Playback p;
        p.select(3, true);
        check(p.frame() == 0 && !p.playing(), "selection starts paused at first frame");
        p.toggle(); check(p.playing(), "play starts a solved multi-frame path");
        p.update(0.49); check(p.frame() == 0, "wait for complete interval");
        p.update(0.01); check(p.frame() == 1 && p.playing(), "advance exactly one frame");
        p.toggle(); p.update(10); check(p.frame() == 1 && !p.playing(), "pause freezes playback");
        p.toggle(); p.update(0.5); check(p.frame() == 2 && !p.playing(), "goal stops playback");
        p.update(10); check(p.frame() == 2, "finished playback stays at goal");
        p.toggle(); check(p.frame() == 0 && p.playing(), "play at end restarts path");
        p.step(1); check(p.frame() == 1 && !p.playing(), "manual step pauses");
        p.step(-1); p.step(-1); check(p.frame() == 0, "step cannot pass first frame");
        p.last(); p.step(1); check(p.frame() == 2 && !p.playing(), "step cannot pass last frame");
        p.first(); check(p.frame() == 0 && !p.playing(), "restart pauses at first frame");
        p.toggle(); p.update(0.25); p.step(1); p.toggle(); p.update(0.25);
        check(p.frame() == 1, "manual actions discard partial interval");
        p.select(1, true); p.toggle(); p.update(10);
        check(p.frame() == 0 && !p.playing(), "zero move path never plays");
        p.select(1, false); p.toggle(); p.step(1); p.last();
        check(p.frame() == 0 && !p.playing(), "unsolvable is display only");
        p.select(3, true); p.toggle(); p.select(2, true);
        check(p.frame() == 0 && !p.playing(), "puzzle switch resets playback");
        bool rejected = false;
        try { p.select(0, true); } catch (const std::invalid_argument&) { rejected = true; }
        check(rejected && p.frame() == 0, "reject empty path without changing selection");
        for (int i = 0; i < 20; ++i) p.faster();
        check(std::abs(p.interval() - 0.125) < 1e-9, "fastest rate bounded at eight frames per second");
        for (int i = 0; i < 20; ++i) p.slower();
        check(std::abs(p.interval() - 2.0) < 1e-9, "slowest rate bounded at half frame per second");
        p.update(-1); p.update(std::nan(""));
        check(p.frame() == 0, "invalid time values do not advance");
        p.toggle(); p.update(1000);
        check(p.frame() == 1 && !p.playing(), "large elapsed time safely clamps to end");
        std::cout << "PASS " << checks << " playback checks\n";
    } catch (const std::exception& e) { std::cerr << "FAIL " << e.what() << '\n'; return 1; }
}
