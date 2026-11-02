#include "path_parser.hpp"
#include <iostream>
#include <sstream>
#include <stdexcept>

static int checks = 0;
static void check(const std::string& text, bool expected, const char* name) {
    np::Path path; std::string error; std::istringstream in(text);
    bool actual = np::parse(in, path, error);
    ++checks;
    if (actual != expected || (!actual && error.empty()))
        throw std::runtime_error(std::string(name) + ": " + error);
    if (actual && (path.frames.empty() || path.goal.size() != static_cast<std::size_t>(path.size * path.size)))
        throw std::runtime_error("accepted path lacks board data");
}
static std::string replace(std::string text, const std::string& from, const std::string& to) {
    auto pos = text.find(from); if (pos == std::string::npos) throw std::runtime_error("bad test fixture");
    text.replace(pos, from.size(), to); return text;
}
int main() {
    const std::string zero = "NP_PATH_V1\nsize 3\nstatus SOLVED\nmoves 0\nstates 1\ngoal 1 2 3 4 5 6 7 8 0\nstate 0 1 2 3 4 5 6 7 8 0\n";
    const std::string one = "NP_PATH_V1\nsize 3\nstatus SOLVED\nmoves 1\nstates 2\ngoal 1 2 3 4 5 6 7 8 0\nstate 0 1 2 3 4 5 6 7 0 8\nstate 1 1 2 3 4 5 6 7 8 0\n";
    try {
        check(zero, true, "zero moves at goal");
        check(one, true, "one legal move");
        check("NP_PATH_V1\nsize 4\nstatus SOLVED\nmoves 0\nstates 1\ngoal 0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15\nstate 0 0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15\n", true, "custom 4x4 goal");
        check(replace(replace(zero,"SOLVED","UNSOLVABLE"),"moves 0","moves -1"), true, "unsolvable display only");
        check("", false, "empty");
        check(replace(zero,"NP_PATH_V1","NP_PATH_V2"), false, "header");
        check(replace(zero,"size 3","size 5"), false, "unsupported size");
        check(replace(zero,"SOLVED","TIMEOUT"), false, "timeout not path");
        check(replace(zero,"moves 0","moves -1"), false, "solved negative moves");
        check(replace(zero,"states 1","states 0"), false, "empty frames");
        check(replace(one,"states 2","states 1"), false, "state count");
        check(replace(one,"moves 1","moves 2"), false, "move count");
        check(replace(one,"state 1","state 2"), false, "ordered steps");
        check(replace(zero,"state 0 1 2","state 0 2 2"), false, "duplicate tile");
        check(replace(zero,"state 0 1 2","state 0 -1 2"), false, "negative tile");
        check(replace(zero,"state 0 1 2","state 0 9 2"), false, "out of range tile");
        check(replace(zero,"state 0 1 2","state 0 x 2"), false, "nonnumeric tile");
        check(replace(one,"state 0 1 2 3 4 5 6 7 0 8","state 0 1 2 3 4 5 6 0 8 7"), false, "nonadjacent blank forbidden");
        check("NP_PATH_V1\nsize 3\nstatus SOLVED\nmoves 1\nstates 2\ngoal 1 2 0 3 4 5 6 7 8\nstate 0 1 2 3 0 4 5 6 7 8\nstate 1 1 2 0 3 4 5 6 7 8\n", false, "row wrap forbidden");
        check(replace(one,"state 0 1 2 3 4 5 6 7 0 8","state 0 2 1 3 4 5 6 7 0 8"), false, "extra tile swap forbidden");
        check(replace(zero,"state 0 1 2","state 0 2 1"), false, "zero move must equal goal");
        check(replace(one,"state 1 1 2 3 4 5 6 7 8 0","state 1 1 2 3 4 5 6 0 7 8"), false, "last frame goal");
        check(zero + "goal 1 2 3 4 5 6 7 8 0\n", false, "duplicate goal");
        check(zero + "garbage\n", false, "trailing garbage");
        check(replace(zero,"size 3","size 3 junk"), false, "extra token");
        check(replace(zero,"moves 0","moves 999999999999999999999"), false, "integer overflow");
        check(replace(zero,"states 1","states -1"), false, "negative count");
        check(replace(replace(one,"SOLVED","UNSOLVABLE"),"moves 1","moves -1"), false, "unsolvable one frame only");
        check(replace(zero,"SOLVED","UNSOLVABLE"), false, "unsolvable minus one moves required");
        check(replace(zero,"goal 1 2","goal 2 2"), false, "goal permutation");
        check(replace(zero,"state 0 1","state -1 1"), false, "negative step");
        check(replace(zero,"state 0 1 2 3 4 5 6 7 8 0", "state 0 1 2 3 4 5 6 7 8"), false, "short board");
        for (std::size_t end = zero.find('\n'); end != std::string::npos && end + 1 < zero.size(); end = zero.find('\n', end + 1))
            check(zero.substr(0, end + 1), false, "truncated file");
        check(std::string("\xEF\xBB\xBF") + zero, true, "UTF8 BOM");
        check(replace(zero,"NP_PATH_V1\n","NP_PATH_V1\r\n"), true, "CRLF");
        std::cout << "PASS " << checks << " parser contract checks\n";
    } catch (const std::exception& e) { std::cerr << "FAIL " << e.what() << '\n'; return 1; }
}
