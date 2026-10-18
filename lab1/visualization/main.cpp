#include "Event.hpp"
#include <SFML/Graphics.hpp>
#include <SFML/Window.hpp>
#include <iostream>
#include <fstream>
#include <vector>
#include <string>
#include <sstream>

using namespace std;

struct PuzzleFrame {
    int step;
    vector<int> tiles;
};

int main() {
    // 1. 读取路径文件（请确保你的路径是对的）
    string filepath = "C:\\Users\\zhangxinrui\\ouc26-AI2\\lab1\\examples\\paths\\one-move-4x4.txt";
    ifstream file(filepath);

    if (!file.is_open()) {
        cout << "无法打开文件: " << filepath << endl;
        system("pause");
        return -1;
    }

    int size = 0;
    vector<PuzzleFrame> frames;
    string line;

    while (getline(file, line)) {
        if (line.empty()) continue;
        stringstream ss(line);
        string prefix;
        ss >> prefix;

        if (prefix == "size") {
            ss >> size;
        }
        else if (prefix == "state") {
            PuzzleFrame frame;
            ss >> frame.step;
            for (int i = 0; i < size * size; ++i) {
                int val;
                ss >> val;
                frame.tiles.push_back(val);
            }
            frames.push_back(frame);
        }
    }
    file.close();

    if (size == 0 || frames.empty()) {
        cout << "文件格式错误或未读取到状态" << endl;
        system("pause");
        return -1;
    }

    // 2. 创建窗口
    sf::RenderWindow window(sf::VideoMode(600, 600), "N-Puzzle Visualization");

    sf::Font font;
    if (!font.loadFromFile("C:/Windows/Fonts/arial.ttf")) {
        cout << "字体加载失败" << endl;
    }

    float tileSize = 600.0f / size;
    int currentFrame = 0;
    sf::Clock clock;

    while (window.isOpen()) {
        sf::Event event;
        while (window.pollEvent(event)) {
            if (event.type == sf::Event::Closed) {
                window.close();
            }
            // 按空格键手动切换下一帧
            if (event.type == sf::Event::KeyPressed && event.key.code == sf::Keyboard::Space) {
                currentFrame = (currentFrame + 1) % frames.size();
            }
        }

        // 自动播放
        if (clock.getElapsedTime().asSeconds() > 0.5f) {
            currentFrame = (currentFrame + 1) % frames.size();
            clock.restart();
        }

        window.clear(sf::Color(30, 30, 30));

        if (!frames.empty()) {
            PuzzleFrame& current = frames[currentFrame];
            for (int i = 0; i < size * size; ++i) {
                int val = current.tiles[i];
                if (val == 0) continue;

                int row = i / size;
                int col = i % size;

                sf::RectangleShape rect(sf::Vector2f(tileSize - 4.0f, tileSize - 4.0f));
                rect.setPosition(col * tileSize + 2.0f, row * tileSize + 2.0f);
                rect.setFillColor(sf::Color(70, 130, 180));
                window.draw(rect);

                sf::Text text;
                text.setFont(font);
                text.setString(to_string(val));
                text.setCharacterSize(static_cast<unsigned int>(tileSize / 2));
                text.setFillColor(sf::Color::White);

                sf::FloatRect textRect = text.getLocalBounds();
                text.setOrigin(textRect.left + textRect.width / 2.0f, textRect.top + textRect.height / 2.0f);
                text.setPosition(col * tileSize + tileSize / 2.0f, row * tileSize + tileSize / 2.0f);

                window.draw(text);
            }
        }
        window.display();
    }
    return 0;
}