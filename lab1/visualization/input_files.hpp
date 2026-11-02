#pragma once
#include "path_parser.hpp"
#include <filesystem>
#include <fstream>
#include <iostream>

namespace np {
namespace fs = std::filesystem;
inline fs::path defaultDirectory(const fs::path& executable) {
    for (auto root : {fs::current_path(), fs::absolute(executable).parent_path()}) {
        while (!root.empty()) {
            for (const auto& relative : {fs::path("lab1/examples/paths"), fs::path("examples/paths")})
                if (fs::is_directory(root / relative)) return root / relative;
            auto parent = root.parent_path(); if (parent == root) break; root = parent;
        }
    }
    throw std::runtime_error("Cannot locate lab1/examples/paths; pass an input directory or file.");
}
struct NamedPath { std::string filename; Path path; };
inline std::vector<NamedPath> load(const fs::path& source, bool& rejected) {
    std::vector<fs::path> files;
    if (fs::is_regular_file(source)) files.push_back(source);
    else if (fs::is_directory(source)) {
        for (const auto& entry : fs::directory_iterator(source))
            if (entry.is_regular_file() && entry.path().extension() == ".txt") files.push_back(entry.path());
    } else throw std::runtime_error("Input does not exist: " + source.u8string());
    std::sort(files.begin(), files.end());
    std::vector<NamedPath> paths; rejected = false;
    for (const auto& file : files) {
        std::ifstream input(file); Path path; std::string error;
        if (!input || !parse(input, path, error)) {
            rejected = true;
            std::cerr << "Rejected " << file.u8string() << ": " << (error.empty() ? "cannot open file" : error) << '\n';
        } else paths.push_back({file.filename().u8string(), std::move(path)});
    }
    if (paths.empty()) throw std::runtime_error("No valid NP_PATH_V1 files found.");
    return paths;
}
}
