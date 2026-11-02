#include "input_files.hpp"
int main(int argc, char** argv) {
    try {
        if (argc > 2) throw std::runtime_error("Usage: path_check [directory-or-file]");
        bool rejected;
        auto paths = np::load(argc == 2 ? np::fs::u8path(argv[1]) : np::defaultDirectory(argv[0]), rejected);
        for (const auto& item : paths)
            std::cout << item.filename << ": " << item.path.status << ", " << item.path.moves << " moves, " << item.path.frames.size() << " states\n";
        return rejected ? 2 : 0;
    } catch (const std::exception& e) { std::cerr << e.what() << '\n'; return 1; }
}
