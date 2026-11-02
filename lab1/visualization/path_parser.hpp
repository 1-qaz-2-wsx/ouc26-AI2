#pragma once
#include <istream>
#include <string>
#include <vector>
#include <algorithm>
#include <cstdlib>
#include <sstream>

namespace np {
struct Frame { int step = 0; std::vector<int> tiles; };
struct Path { int size = 0; std::string status; int moves = 0; std::vector<int> goal; std::vector<Frame> frames; };
// No SFML dependency: malformed input never exposes partial data to the player.
inline bool parse(std::istream& input, Path& output, std::string& error) {
    output = Path{}; error.clear(); Path data;
    std::string line; std::size_t lineNumber = 0;
    auto fail = [&](const std::string& message) {
        error = "line " + std::to_string(lineNumber) + ": " + message; return false;
    };
    auto next = [&]() {
        while (std::getline(input, line)) {
            ++lineNumber;
            if (lineNumber == 1 && line.compare(0, 3, "\xEF\xBB\xBF") == 0) line.erase(0, 3);
            if (line.find_first_not_of(" \t\r") != std::string::npos) return true;
        }
        return false;
    };
    auto scalar = [&](const std::string& key, auto& value) {
        if (!next()) return false;
        std::istringstream row(line); std::string label, extra;
        return bool(row >> label >> value) && label == key && !(row >> extra);
    };
    auto board = [&](std::istringstream& row, std::vector<int>& tiles) {
        std::vector<bool> seen(static_cast<std::size_t>(data.size * data.size), false);
        for (int i = 0; i < data.size * data.size; ++i) {
            int tile;
            if (!(row >> tile) || tile < 0 || tile >= data.size * data.size || seen[tile]) return false;
            seen[tile] = true; tiles.push_back(tile);
        }
        std::string extra; return !(row >> extra);
    };
    if (!next()) return fail("missing NP_PATH_V1 header");
    { std::istringstream row(line); std::string header, extra;
      if (!(row >> header) || header != "NP_PATH_V1" || (row >> extra)) return fail("expected NP_PATH_V1 header"); }
    if (!scalar("size", data.size) || (data.size != 3 && data.size != 4)) return fail("size must be 3 or 4");
    if (!scalar("status", data.status) || (data.status != "SOLVED" && data.status != "UNSOLVABLE"))
        return fail("status must be SOLVED or UNSOLVABLE; TIMEOUT is not a path");
    if (!scalar("moves", data.moves)) return fail("invalid moves");
    int states = 0;
    if (!scalar("states", states) || states < 1) return fail("states must be positive");
    if (data.status == "SOLVED") {
        if (data.moves < 0 || static_cast<long long>(data.moves) + 1 != states) return fail("SOLVED requires states = moves + 1");
    } else if (data.moves != -1 || states != 1) return fail("UNSOLVABLE requires moves -1 and states 1");
    if (!next()) return fail("missing goal");
    { std::istringstream row(line); std::string key;
      if (!(row >> key) || key != "goal" || !board(row, data.goal)) return fail("goal must be one tile permutation"); }
    for (int step = 0; step < states; ++step) {
        if (!next()) return fail("missing state " + std::to_string(step));
        std::istringstream row(line); std::string key; Frame frame;
        if (!(row >> key >> frame.step) || key != "state" || frame.step != step || !board(row, frame.tiles))
            return fail("state must have ordered index and one tile permutation");
        if (!data.frames.empty()) {
            const auto& prev = data.frames.back().tiles;
            auto a = std::find(prev.begin(), prev.end(), 0) - prev.begin();
            auto b = std::find(frame.tiles.begin(), frame.tiles.end(), 0) - frame.tiles.begin();
            if (std::abs(a / data.size - b / data.size) + std::abs(a % data.size - b % data.size) != 1)
                return fail("blank must move to adjacent square");
            auto expected = prev; std::swap(expected[a], expected[b]);
            if (expected != frame.tiles) return fail("each transition must swap only blank and adjacent tile");
        }
        data.frames.push_back(std::move(frame));
    }
    if (next()) return fail("unexpected data after final state");
    if (input.bad()) return fail("input read failed");
    if (data.status == "SOLVED" && data.frames.back().tiles != data.goal) return fail("final state must equal goal");
    output = std::move(data); return true;
}
}
