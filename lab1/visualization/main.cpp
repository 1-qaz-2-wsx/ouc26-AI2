// D's original SFML player: 7be5268. Leader repair: portability and playback.
#include "input_files.hpp"
#include "playback.hpp"
#include <SFML/Graphics.hpp>
#include <array>
#include <cstdlib>
#include <iomanip>

namespace {
constexpr float width = 900, height = 820;
void fitView(sf::RenderWindow& window, unsigned w, unsigned h) {
    if (!w || !h) return;
    sf::View view(sf::FloatRect(0, 0, width, height));
    float ratio = static_cast<float>(w) / h, target = width / height;
    if (ratio > target) view.setViewport({(1 - target / ratio) / 2, 0, target / ratio, 1});
    else view.setViewport({0, (1 - ratio / target) / 2, 1, ratio / target});
    window.setView(view);
}
}

int main(int argc, char** argv) {
    try {
        np::fs::path source, fontPath;
        for (int i = 1; i < argc; ++i) {
            std::string arg = argv[i];
            if (arg == "--font" && i + 1 < argc) fontPath = np::fs::u8path(argv[++i]);
            else if (arg == "--help") {
                std::cout << "Usage: npuzzle_player [directory-or-file] [--font font.ttf]\n"
                             "Space: play/pause; Left/Right: puzzle; Up/Down: step;\n"
                             "Home/End: first/last; +/-: speed; Escape: exit. Mouse buttons supported.\n";
                return 0;
            } else if (!arg.empty() && arg[0] != '-' && source.empty()) source = np::fs::u8path(arg);
            else throw std::runtime_error("Invalid argument; use --help.");
        }
        if (source.empty()) source = np::defaultDirectory(argv[0]);
        bool rejected;
        auto puzzles = np::load(source, rejected);
        if (rejected) std::cerr << "Some files rejected; only validated paths will be displayed.\n";
        sf::Font font;
        if (fontPath.empty()) {
            for (const auto& candidate : {np::fs::current_path() / "resources/DejaVuSans.ttf",
                                         np::fs::absolute(argv[0]).parent_path() / "resources/DejaVuSans.ttf"})
                if (np::fs::is_regular_file(candidate)) { fontPath = candidate; break; }
#ifdef _WIN32
            if (fontPath.empty()) if (const char* windows = std::getenv("WINDIR")) {
                for (const char* name : {"msyh.ttc", "simhei.ttf", "arial.ttf"}) {
                    auto candidate = np::fs::path(windows) / "Fonts" / name;
                    if (np::fs::is_regular_file(candidate)) { fontPath = candidate; break; }
                }
            }
#endif
        }
        if (fontPath.empty() || !font.loadFromFile(fontPath.u8string()))
            throw std::runtime_error("Cannot load font; pass --font with an existing font file.");
        sf::RenderWindow window(sf::VideoMode(900, 820), "N-Puzzle Player");
        window.setFramerateLimit(60); window.setKeyRepeatEnabled(false); fitView(window, 900, 820);
        std::size_t puzzleIndex = 0;
        np::Playback playback;
        auto select = [&]() { const auto& p = puzzles[puzzleIndex].path; playback.select(p.frames.size(), p.status == "SOLVED"); };
        select();
        auto switchPuzzle = [&](int direction) {
            puzzleIndex = direction < 0 ? (puzzleIndex + puzzles.size() - 1) % puzzles.size() : (puzzleIndex + 1) % puzzles.size(); select();
        };
        std::array<sf::FloatRect, 8> buttons;
        for (std::size_t i = 0; i < buttons.size(); ++i) buttons[i] = {20.f + i * 107.f, 727.f, 99.f, 36.f};
        auto action = [&](int index) {
            switch (index) {
                case 0: switchPuzzle(-1); break;
                case 1: playback.step(-1); break;
                case 2: playback.toggle(); break;
                case 3: playback.step(1); break;
                case 4: switchPuzzle(1); break;
                case 5: playback.first(); break;
                case 6: playback.slower(); break;
                case 7: playback.faster(); break;
            }
        };
        sf::Clock frameClock;
        while (window.isOpen()) {
            double seconds = frameClock.restart().asSeconds();
            sf::Event event{};
            while (window.pollEvent(event)) {
                if (event.type == sf::Event::Closed) window.close();
                else if (event.type == sf::Event::Resized) fitView(window, event.size.width, event.size.height);
                else if (event.type == sf::Event::MouseButtonPressed && event.mouseButton.button == sf::Mouse::Left) {
                    auto point = window.mapPixelToCoords({event.mouseButton.x, event.mouseButton.y});
                    for (std::size_t i = 0; i < buttons.size(); ++i) if (buttons[i].contains(point)) { action(static_cast<int>(i)); seconds = 0; break; }
                } else if (event.type == sf::Event::KeyPressed) {
                    seconds = 0;
                    switch (event.key.code) {
                        case sf::Keyboard::Escape: window.close(); break;
                        case sf::Keyboard::Left: action(0); break;
                        case sf::Keyboard::Up: action(1); break;
                        case sf::Keyboard::Space: action(2); break;
                        case sf::Keyboard::Down: action(3); break;
                        case sf::Keyboard::Right: action(4); break;
                        case sf::Keyboard::Home: action(5); break;
                        case sf::Keyboard::End: playback.last(); break;
                        case sf::Keyboard::Subtract: case sf::Keyboard::Hyphen: action(6); break;
                        case sf::Keyboard::Add: case sf::Keyboard::Equal: action(7); break;
                        default: break;
                    }
                }
            }
            if (!window.isOpen()) break;
            playback.update(seconds);
            const auto& item = puzzles[puzzleIndex]; const auto& path = item.path;
            bool solved = path.status == "SOLVED", playable = solved && path.frames.size() > 1;
            window.clear(sf::Color(27, 31, 38));
            auto label = [&](const sf::String& text, float x, float y, unsigned size, sf::Color color) {
                sf::Text shape(text, font, size); shape.setPosition(x, y); shape.setFillColor(color); window.draw(shape);
            };
            std::string name = "Puzzle " + std::to_string(puzzleIndex + 1) + "/" + std::to_string(puzzles.size()) + "   " + item.filename;
            sf::String title = sf::String::fromUtf8(name.begin(), name.end());
            sf::Text measured(title, font, 20);
            while (title.getSize() > 4 && measured.getLocalBounds().width > 850) {
                title.erase(title.getSize() - 4, 1); title.replace(title.getSize() - 3, 3, "..."); measured.setString(title);
            }
            label(title, 20, 10, 20, sf::Color::White);
            std::ostringstream status;
            status << (solved ? "Solved" : "No solution - display only") << "   |   Step " << playback.frame() << "/" << path.frames.size() - 1;
            if (solved) status << "   |   " << (playback.playing() ? "Playing" : playback.frame() + 1 == path.frames.size() ? "At goal" : "Paused");
            status << "   |   " << std::fixed << std::setprecision(3) << playback.interval() << " s/step";
            label(status.str(), 20, 44, 18, solved ? sf::Color(163, 220, 190) : sf::Color(244, 190, 117));
            const float tileSize = 600.f / path.size, left = 150.f, top = 90.f;
            const auto& tiles = path.frames[playback.frame()].tiles;
            for (int i = 0; i < path.size * path.size; ++i) {
                float x = left + (i % path.size) * tileSize, y = top + (i / path.size) * tileSize;
                sf::RectangleShape rect({tileSize - 4.f, tileSize - 4.f}); rect.setPosition(x + 2, y + 2);
                rect.setFillColor(tiles[i] == 0 ? sf::Color(43, 48, 57) : sf::Color(64, 123, 171)); window.draw(rect);
                if (tiles[i]) {
                    sf::Text text(std::to_string(tiles[i]), font, static_cast<unsigned>(tileSize / 2));
                    const auto bounds = text.getLocalBounds(); text.setOrigin(bounds.left + bounds.width / 2, bounds.top + bounds.height / 2);
                    text.setPosition(x + tileSize / 2, y + tileSize / 2); window.draw(text);
                }
            }
            float progress = path.frames.size() > 1 ? static_cast<float>(playback.frame()) / (path.frames.size() - 1) : 1;
            sf::RectangleShape track({600, 5}); track.setPosition(150, 704); track.setFillColor(sf::Color(48, 55, 65)); window.draw(track);
            track.setSize({600 * progress, 5}); track.setFillColor(solved ? sf::Color(93, 184, 145) : sf::Color(155, 131, 97)); window.draw(track);
            std::array<std::string, 8> names = {"Prev puzzle", "Prev step", playback.playing() ? "Pause" : "Play", "Next step", "Next puzzle", "Restart", "Slower", "Faster"};
            for (std::size_t i = 0; i < buttons.size(); ++i) {
                bool enabled = (i == 2) ? playable : (i == 1) ? playback.frame() > 0 : (i == 3) ? playback.frame() + 1 < path.frames.size() : true;
                sf::RectangleShape button({buttons[i].width, buttons[i].height}); button.setPosition(buttons[i].left, buttons[i].top);
                button.setFillColor(enabled ? sf::Color(54, 67, 84) : sf::Color(36, 42, 51)); window.draw(button);
                label(names[i], buttons[i].left + 7, buttons[i].top + 7, 14, enabled ? sf::Color::White : sf::Color(120, 128, 140));
            }
            label("Space play/pause    Arrows puzzle/step    Home/End first/last    +/- speed    Esc close", 20, 781, 15, sf::Color(169, 178, 191));
            window.display();
        }
        return 0;
    } catch (const std::exception& e) { std::cerr << "Player: " << e.what() << '\n'; return 1; }
}
