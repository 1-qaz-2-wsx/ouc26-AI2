package stud.g01.problem.npuzzle;

import core.problem.Action;

/**
 * 空格移动动作：封装一个移动方向，单步代价恒为 1。
 * PuzzleMove(Direction)、direction()、
 * stepCost()（恒为 1）和 draw()。
 */
public class PuzzleMove extends Action {

    /** 本动作对应的移动方向 */
    private final Direction direction;

    /**
     * 构造一个移动动作。
     * @param direction 移动方向
     */
    public PuzzleMove(Direction direction) {
        this.direction = direction;
    }

    /**
     * 返回本动作的移动方向。
     * @return 移动方向
     */
    public Direction direction() {
        return direction;
    }

    /**
     * 单步代价：空格每移动一格记 1。
     * @return 恒为 1
     */
    @Override
    public int stepCost() {
        return 1;
    }

    /**
     * 在控制台上打印本动作，例如 "RIGHT"。
     */
    @Override
    public void draw() {
        System.out.println(this);
    }

    @Override
    public String toString() {
        return direction.name();
    }

    /**
     * 两个动作相等，当且仅当方向相同。
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj instanceof PuzzleMove) {
            return this.direction == ((PuzzleMove) obj).direction;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return direction.hashCode();
    }
}
