package web.scraper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class Cleaner extends Thread {
    private IndexURLTree tree;
    private List<List<Pair<String,String>>> buffers;

    public Cleaner(IndexURLTree tree, List<List<Pair<String,String>>> buffers) {
        this.tree = tree;
        this.buffers = buffers;
    }

    public void run() {
        System.out.println("\nStart cleaning............................");
        writeRemainingToTree();
        writeFromTreeToDisk();
        writeStatsToDisk();
        System.out.println("Done!!!!!!!!!!!!!!!!!!!!!!!!!");
    }

    public void writeRemainingToTree() {
        buffers.forEach(buffer -> {
            buffer.forEach(pair -> tree.addURLandContent(pair.head(), pair.tail()));
        });
    }

    public void writeFromTreeToDisk() {
        try {
            File file = new File("./result.txt");
            file.createNewFile();

            FileWriter writer = new FileWriter(file);

            // TODO: traverse entire directory tree to write URLs (and HTML?) into file
            // for (String url : tree) {
            //     writer.write(url);
            //     writer.write("\n");
            // }

           writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        } 
    }

    public void writeStatsToDisk() {
        try {
            File file = new File("./statistics.txt");
            file.createNewFile();

            FileWriter writer = new FileWriter(file);
            writer.write(".............Stats............\n");
            // TODO: add findURLs() method to calculate no. of URLs in IUT
            writer.write(String.format("%d new urls are found.", tree.size()));
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}