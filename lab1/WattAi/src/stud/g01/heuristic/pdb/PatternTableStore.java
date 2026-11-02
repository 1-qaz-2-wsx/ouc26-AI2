package stud.g01.heuristic.pdb;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import stud.g01.problem.npuzzle.PuzzleBoard;

/** v3单组快照。流式CRC，不复制整张大表；已完成组可跨分组方案复用。 */
final class PatternTableStore {
    private static final int MAGIC=0x50444733,VERSION=3;
    static Path path(PuzzleBoard goal,int[] pattern,Path directory){
        try{
            String identity=goal.size()+Arrays.toString(goal.toArray())+Arrays.toString(pattern);
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
            return directory.resolve("group-v3-"+HexFormat.of().formatHex(digest,0,12)+".pdb");
        }catch(NoSuchAlgorithmException ex){throw new AssertionError(ex);}
    }
    static DensePatternTable load(PuzzleBoard goal,int[] pattern,Path directory)throws IOException{
        Path path=path(goal,pattern,directory);
        long states=1;for(int i=0;i<=pattern.length;i++)states*=goal.size()*goal.size()-i;
        long expected=12L+goal.size()*goal.size()*4L+4+pattern.length*4L+4+states+8;
        if(Files.size(path)!=expected)throw new IOException("Invalid group PDB length: "+path);
        try(var raw=new BufferedInputStream(Files.newInputStream(path),1<<20)){
            CRC32 crc=new CRC32();var in=new DataInputStream(new CheckedInputStream(raw,crc));
            if(in.readInt()!=MAGIC||in.readInt()!=VERSION||in.readInt()!=goal.size())throw new IOException("Group version/size mismatch");
            for(int tile:goal.toArray())if(in.readInt()!=tile)throw new IOException("Group target mismatch");
            if(in.readInt()!=pattern.length)throw new IOException("Group pattern length mismatch");
            for(int tile:pattern)if(in.readInt()!=tile)throw new IOException("Group pattern mismatch");
            if(in.readInt()!=states)throw new IOException("Group state count mismatch");
            DensePatternTable table=new DensePatternTable(goal.size()*goal.size(),pattern);
            in.readFully(table.distances);
            long calculated=crc.getValue(),stored=new DataInputStream(raw).readLong();
            if(calculated!=stored||raw.read()!=-1)throw new IOException("Group CRC/trailing data mismatch");
            for(byte value:table.distances)if(Byte.toUnsignedInt(value)>80)throw new IOException("Invalid group distance");
            if(table.estimate(DensePatternTable.locations(goal))!=0)throw new IOException("Nonzero group target");
            return table;
        }
    }
    static void save(PuzzleBoard goal,DensePatternTable table,Path directory)throws IOException{
        Files.createDirectories(directory);
        Path target=path(goal,table.pattern,directory),tmp=Files.createTempFile(directory,"group-",".tmp");
        try{
            try(var raw=new BufferedOutputStream(Files.newOutputStream(tmp),1<<20)){
                CRC32 crc=new CRC32();var out=new DataOutputStream(new CheckedOutputStream(raw,crc));
                out.writeInt(MAGIC);out.writeInt(VERSION);out.writeInt(goal.size());
                for(int tile:goal.toArray())out.writeInt(tile);
                out.writeInt(table.pattern.length);for(int tile:table.pattern)out.writeInt(tile);
                out.writeInt(table.distances.length);out.write(table.distances);out.flush();
                var footer=new DataOutputStream(raw);footer.writeLong(crc.getValue());footer.flush();
            }
            try{Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException ex){Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(tmp);}
    }
}
