/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package web.scraper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class App {
    // TreeSet and LinkedList is NOT thread safe!!!
    // Visit
    // https://riptutorial.com/java/example/30472/treemap-and-treeset-thread-safety
    // for how to ensure thread safety using TreeSet.
    private Logger logger;
    private TreeSet<String> tree;
    private List<List<String>> buffers;

    public App() {
        this.logger = Logger.getLogger("App");
        this.tree = new TreeSet<>();
        this.buffers = new LinkedList<>();
        IntStream.range(0, 2).forEach(x -> buffers.add(new LinkedList<>()));
    }

    public void run() {
        logger.info("Starting........ =D");
        initialise();

        List<String> seeds = getURLSeeds();
        List<List<String>> subLists = splitList(seeds, 4);

        Crawler crawler1 = new Crawler(subLists.get(0), tree, this.buffers.get(0));
        Crawler crawler2 = new Crawler(subLists.get(1), tree, this.buffers.get(0));
        Crawler crawler3 = new Crawler(subLists.get(2), tree, this.buffers.get(1));
        Crawler crawler4 = new Crawler(subLists.get(3), tree, this.buffers.get(1));

        IndexBuilder indexBuilder = new IndexBuilder(tree, this.buffers.get(0));

        crawler1.start();
        crawler2.start();
        crawler3.start();
        crawler4.start();

        Thread ib1 = new Thread(indexBuilder);
        // ib1.start();

        try {
            crawler1.join();
            logger.info(String.format("crawler %d joined...............................", crawler1.getId()));

            crawler2.join();
            logger.info(String.format("crawler %d joined...............................", crawler2.getId()));

            crawler3.join();
            logger.info(String.format("crawler %d joined...............................", crawler3.getId()));

            crawler4.join();
            logger.info(String.format("crawler %d joined...............................", crawler4.getId()));
        } catch (InterruptedException e) {
            logger.severe(e.getMessage());
        }
    }

    public void initialise() {
        Runtime.getRuntime().addShutdownHook(new Cleaner(this.tree, this.buffers));

        // The following 2 line removes log from the following 2 sources.
        Logger.getLogger("com.gargoylesoftware").setLevel(Level.OFF);
        Logger.getLogger("org.apache.commons.httpclient").setLevel(Level.OFF);
    }

    // Read urls from seed file.
    public List<String> getURLSeeds() {
        InputStreamReader reader = new InputStreamReader(System.in);
        BufferedReader bufReader = new BufferedReader(reader);
        return bufReader.lines().collect(Collectors.toList());
    }

    // Split the given url list into the specified number of sub lists.
    // Condition: number of urls in list given >= num of sublists
    public List<List<String>> splitList(List<String> list, int numSubLists) {
        int portionSize = list.size() / numSubLists;

        List<List<String>> result = new LinkedList<>();
        List<String> temp = new LinkedList<>();
        int count = 0;
        int currNumSubLists = 0;

        for (String url : list) {
            temp.add(url);
            count++;

            if (count == portionSize && currNumSubLists < numSubLists - 1) {
                result.add(temp);
                temp = new LinkedList<>();
                currNumSubLists++;
                count = 0;
            }
        }
        result.add(temp);

        return result;
    }

    public static void main(String[] args) {
        new App().run();
    }
}
