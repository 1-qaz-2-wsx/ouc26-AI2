package stud.g01.runner;

/**
 * 阶段三 A* 实验入口。
 *
 * 复用 Phase2Experiment 中已经完成的：
 *
 * 1. Open/Closed 四种策略比较；
 * 2. 三种启发函数比较；
 * 3. 预热运行；
 * 4. 正式运行；
 * 5. 路径合法性检查；
 * 6. 搜索统计稳定性检查；
 * 7. 原始 CSV 和汇总 CSV 输出。
 *
 * 本类不修改 Phase2Experiment.java。
 *
 * 默认输出：
 *
 * experiments/astar-phase3-raw.csv
 * experiments/astar-phase3-summary.csv
 */
public final class Phase3AstarExperiment {

    private static final String DEFAULT_INPUT =
            "WattAi/resources/test_8puzzle.txt";

    private static final String DEFAULT_RAW_OUTPUT =
            "experiments/astar-phase3-raw.csv";

    private static final String DEFAULT_SUMMARY_OUTPUT =
            "experiments/astar-phase3-summary.csv";

    private static final String DEFAULT_PDB_DIRECTORY =
            "lab1/.local/pdb-phase3";

    private Phase3AstarExperiment() {
    }

    public static void main(String[] args) throws Exception {
        String inputFile =
                args.length >= 1
                        ? args[0]
                        : DEFAULT_INPUT;

        String rawOutput =
                args.length >= 2
                        ? args[1]
                        : DEFAULT_RAW_OUTPUT;

        String summaryOutput =
                args.length >= 3
                        ? args[2]
                        : DEFAULT_SUMMARY_OUTPUT;

        String pdbDirectory =
                args.length >= 4
                        ? args[3]
                        : DEFAULT_PDB_DIRECTORY;

        String[] phase2Arguments = {
                inputFile,
                rawOutput,
                summaryOutput,
                pdbDirectory
        };

        System.out.println(
                "Starting Phase 3 A* experiment.");

        System.out.println(
                "Input: " + inputFile);

        System.out.println(
                "Raw output: " + rawOutput);

        System.out.println(
                "Summary output: " + summaryOutput);

        System.out.println(
                "PDB directory: " + pdbDirectory);

        System.out.println(
                "Phase 2 Experiment source is unchanged.");

        Phase2Experiment.main(phase2Arguments);

        System.out.println();
        System.out.println(
                "Phase 3 A* experiment completed.");
    }
}