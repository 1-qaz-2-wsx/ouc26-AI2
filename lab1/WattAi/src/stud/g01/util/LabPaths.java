package stud.g01.util;
import java.nio.file.*;

/** 同时支持仓库根目录和IDEA的WattAi工作目录，不写死本机路径。 */
public final class LabPaths {
    private LabPaths() {}
    public static Path labRoot(){
        for(Path p=Path.of("").toAbsolutePath().normalize();p!=null;p=p.getParent()){
            if(Files.isDirectory(p.resolve("WattAi/src/core")))return p;
            if(Files.isDirectory(p.resolve("lab1/WattAi/src/core")))return p.resolve("lab1");
        }
        throw new IllegalStateException("工作目录须设为仓库AI2或lab1/WattAi");
    }
    public static Path pdbDirectory(){
        String p=System.getProperty("npuzzle.pdb.dir");
        return p==null||p.isBlank()?labRoot().resolve(".local/leader-pdb"):Path.of(p).toAbsolutePath();
    }
}
