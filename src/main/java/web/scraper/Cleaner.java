package web.scraper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.TreeSet;
import java.util.List;

public class Cleaner extends Thread {
    private TreeSet<String> tree;
    private List<List<String>> buffers;

    public Cleaner(TreeSet<String> tree, List<List<String>> buffers) {
        this.tree = tree;
        this.buffers = buffers;
    }

    public void run() {
        System.out.println("\nStart cleaning............................");
        writeRemainingToTree();
        writeToDisk();
        System.out.println("Done!!!!!!!!!!!!!!!!!!!!!!!!!");
    }

    public void writeRemainingToTree() {
        buffers.forEach(buffer -> {
            buffer.forEach(url -> tree.add(url));
        });
    }

    public void writeToDisk() {
        try {
            File file = new File("./result.txt");
            file.createNewFile();

            FileWriter writer = new FileWriter(file);

            for (String url : tree) {
                writer.write(url);
                writer.write("\n");
            }

           writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        } 
    }
}