#include <SFML/Window/Keyboard.hpp>
#include <SFML/Window/Event.hpp>
#include <SFML/Graphics.hpp>
#include <SFML/Window.hpp>
#include <iostream>
#include <fstream>
#include <vector>
#include <string>
#include <sstream>
#include <algorithm>
#include <windows.h>

using namespace std;

struct PuzzleFrame {
    int step;
    vector<int> tiles;
};

struct PuzzleData {
    string filename;
    int size = 0;
    string status;
    vector<PuzzleFrame> frames;
};

PuzzleData loadPuzzleFile(const string& filepath) {
    PuzzleData data;
    size_t pos = filepath.find_last_of("\\/");
    data.filename = (pos == string::npos) ? filepath : filepath.substr(pos + 1);

    ifstream file(filepath);
    if (!file.is_open()) {
        cout << "无法打开文件: " << filepath << endl;
        return data;
    }

    string line;
    while (getline(file, line)) {
        if (line.empty()) continue;
        stringstream ss(line);
        string prefix;
        ss >> prefix;

        if (prefix == "size") {
            ss >> data.size;
        }
        else if (prefix == "status") {
            ss >> data.status;
        }
        else if (prefix == "state") {
            PuzzleFrame frame;
            ss >> frame.step;
            for (int i = 0; i < data.size * data.size; ++i) {
                int val;
                ss >> val;
                frame.tiles.push_back(val);
            }
            data.frames.push_back(frame);
        }
    }
    return data;
}

int main() {
    string folderPath = "C:/Users/zhangxinrui/ouc26-AI2/lab1/examples/paths\\*";
    vector<string> filePaths;
    WIN32_FIND_DATAA findData;
    HANDLE hFind = FindFirstFileA(folderPath.c_str(), &findData);

    if (hFind != INVALID_HANDLE_VALUE) {
        do {
            string fileName = findData.cFileName;
            if (fileName != "." && fileName != ".." && fileName.find(".txt") != string::npos) {
                string fullPath = "C:/Users/zhangxinrui/ouc26-AI2/lab1/examples/paths/" + fileName;
                filePaths.push_back(fullPath);
            }
        } while (FindNextFileA(hFind, &findData));
        FindClose(hFind);
    }

    if (filePaths.empty()) {
        cout << "文件夹中没有找到 .txt 文件！" << endl;
        system("pause");
        return -1;
    }
    sort(filePaths.begin(), filePaths.end());

    vector<PuzzleData> allPuzzles;
    for (const auto& path : filePaths) {
        allPuzzles.push_back(loadPuzzleFile(path));
    }

    int windowWidth = 800;
    int windowHeight = 700;
    sf::RenderWindow window(sf::VideoMode(windowWidth, windowHeight), "N-Puzzle Player");

    sf::Font font;
    if (!font.loadFromFile("C:/Windows/Fonts/arial.ttf")) {
        cout << "字体加载失败" << endl;
    }

    int currentPuzzleIndex = 0;
    int currentFrame = 0;
    bool isPlaying = true;
    sf::Clock clock;

    while (window.isOpen()) {
        sf::Event event;
        while (window.pollEvent(event)) {
            if (event.type == sf::Event::Closed) window.close();

            if (event.type == sf::Event::KeyPressed) {
                if (event.key.code == sf::Keyboard::Escape) window.close();
                if (event.key.code == sf::Keyboard::Space) isPlaying = !isPlaying;

                if (event.key.code == sf::Keyboard::Left) {
                    currentPuzzleIndex = (currentPuzzleIndex - 1 + allPuzzles.size()) % allPuzzles.size();
                    currentFrame = 0;
                }
                if (event.key.code == sf::Keyboard::Right) {
                    currentPuzzleIndex = (currentPuzzleIndex + 1) % allPuzzles.size();
                    currentFrame = 0;
                }

                if (event.key.code == sf::Keyboard::Up) {
                    if (currentFrame > 0) currentFrame--;
                    isPlaying = false;
                }
                if (event.key.code == sf::Keyboard::Down) {
                    if (currentFrame < allPuzzles[currentPuzzleIndex].frames.size() - 1) currentFrame++;
                    isPlaying = false;
                }
            }
        }

        if (isPlaying && allPuzzles[currentPuzzleIndex].frames.size() > 1) {
            if (clock.getElapsedTime().asSeconds() > 0.5f) {
                currentFrame = (currentFrame + 1) % allPuzzles[currentPuzzleIndex].frames.size();
                clock.restart();
            }
        }

        window.clear(sf::Color(30, 30, 30));

        PuzzleData& currentPuzzle = allPuzzles[currentPuzzleIndex];

        sf::Text infoText;
        infoText.setFont(font);
        infoText.setCharacterSize(18);
        infoText.setFillColor(sf::Color::White);

        string statusStr = "Status: " + currentPuzzle.status;
        string fileNameStr = "File: [" + to_string(currentPuzzleIndex + 1) + "/" + to_string(allPuzzles.size()) + "] " + currentPuzzle.filename;
        string playStatus = isPlaying ? "[Playing]" : "[Paused]";

        infoText.setString(fileNameStr + "  |  " + statusStr + "  |  Step: " +
            to_string(currentFrame) + "/" + to_string(currentPuzzle.frames.size() - 1) + "  " + playStatus);
        infoText.setPosition(20, 20);
        window.draw(infoText);

        if (!currentPuzzle.frames.empty() && currentPuzzle.size > 0) {
            int size = currentPuzzle.size;
            float boardSize = 600.0f;
            float tileSize = boardSize / size;
            float startX = (windowWidth - boardSize) / 2.0f;
            float startY = 80.0f;

            PuzzleFrame& frame = currentPuzzle.frames[currentFrame];

            for (int i = 0; i < size * size; ++i) {
                int val = frame.tiles[i];
                int row = i / size;
                int col = i % size;

                sf::RectangleShape rect(sf::Vector2f(tileSize - 4.0f, tileSize - 4.0f));
                rect.setPosition(startX + col * tileSize + 2.0f, startY + row * tileSize + 2.0f);

                if (val == 0) {
                    rect.setFillColor(sf::Color(50, 50, 50));
                }
                else {
                    rect.setFillColor(sf::Color(70, 130, 180));
                }
                window.draw(rect);

                if (val != 0) {
                    sf::Text text;
                    text.setFont(font);
                    text.setString(to_string(val));
                    text.setCharacterSize(static_cast<unsigned int>(tileSize / 2));
                    text.setFillColor(sf::Color::White);

                    sf::FloatRect textRect = text.getLocalBounds();
                    text.setOrigin(textRect.left + textRect.width / 2.0f, textRect.top + textRect.height / 2.0f);
                    text.setPosition(startX + col * tileSize + tileSize / 2.0f, startY + row * tileSize + tileSize / 2.0f);
                    window.draw(text);
                }
            }
        }

        sf::Text helpText;
        helpText.setFont(font);
        helpText.setCharacterSize(16);
        helpText.setFillColor(sf::Color(150, 150, 150));
        helpText.setString("Controls: [Space] Play/Pause | [Left/Right] Prev/Next Puzzle | [Up/Down] Prev/Next Step");
        helpText.setPosition(20, windowHeight - 40);
        window.draw(helpText);

        window.display();
    }
    return 0;
}