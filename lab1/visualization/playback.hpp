#pragma once
#include <cstddef>
#include <stdexcept>
#include <algorithm>
#include <cmath>

namespace np {
// Playback is independent of SFML so boundary behavior can be tested directly.
class Playback {
    std::size_t frames_ = 1, frame_ = 0;
    bool solved_ = false, playing_ = false;
    double elapsed_ = 0;
    int rate_ = 2;
    void pause() { playing_ = false; elapsed_ = 0; }
public:
    void select(std::size_t frames, bool solved) {
        if (!frames) throw std::invalid_argument("Cannot select an empty path");
        frames_ = frames; solved_ = solved; frame_ = 0; pause();
    }
    std::size_t frame() const { return frame_; }
    bool playing() const { return playing_; }
    double interval() const {
        static constexpr double rates[] = {0.125, 0.25, 0.5, 1.0, 2.0};
        return rates[rate_];
    }
    void toggle() {
        elapsed_ = 0;
        if (!solved_ || frames_ < 2) return;
        if (!playing_ && frame_ + 1 == frames_) frame_ = 0;
        playing_ = !playing_;
    }
    void step(int direction) {
        pause();
        if (direction < 0 && frame_ > 0) --frame_;
        else if (direction > 0 && frame_ + 1 < frames_) ++frame_;
    }
    void first() { pause(); frame_ = 0; }
    void last() { pause(); frame_ = frames_ - 1; }
    void faster() { rate_ = std::max(0, rate_ - 1); elapsed_ = 0; }
    void slower() { rate_ = std::min(4, rate_ + 1); elapsed_ = 0; }
    void update(double seconds) {
        if (!playing_ || !std::isfinite(seconds) || seconds < 0) return;
        // Never accumulate more than the remaining path: a stalled window stays bounded.
        elapsed_ = std::min(elapsed_ + seconds, interval() * (frames_ - 1 - frame_));
        while (playing_ && elapsed_ + 1e-9 >= interval()) {
            elapsed_ -= interval(); ++frame_;
            if (frame_ + 1 == frames_) pause();
        }
    }
};
}
