/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package web.scraper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.*;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class App implements Callable<Void> {
    // Buffer size is used to determine the number of permits in each crawler semaphore.
    public static final int BUFFER_SIZE = 1000;
    public static int runtime;
    public static int numPagesToStore;
    public static String inputFileName;
    public static String outputFileName;
    private Logger logger;
    private IndexURLTree tree;
    private List<List<Data>> buffers;
    private List<List<Seed>> queues; // synchronised in the App constructor
    private List<IndexBuilder> builders;
    private List<Thread> threads;

    public App() {
        this.logger = Logger.getLogger("App");
        this.tree = new IndexURLTree(outputFileName, App.numPagesToStore);
        this.buffers = new LinkedList<>();
        IntStream.range(0, 3).forEach(x -> buffers.add(Collections.synchronizedList(new LinkedList<>())));
        this.queues = new LinkedList<>();
        this.builders = new LinkedList<>();
        this.threads = new LinkedList<>();
    }

    public Void call() {
        logger.info("Starting........ =D");
        initialise();

        // Need to use addAll instead of directly assigining to queues because initialise() is put before getURLSeeds()
        List<Seed> seeds = getURLSeeds();
        this.queues.addAll(splitList(seeds, 6));

        List<Semaphore> crawlerSemaphores = getCrawlerSemaphores(3);
        List<Semaphore> builderSemaphores = getBuilderSempahores(3);

        Crawler crawler1 = new Crawler(queues.get(0), tree, this.buffers.get(0), crawlerSemaphores.get(0),
            builderSemaphores.get(0));
        Crawler crawler2 = new Crawler(queues.get(1), tree, this.buffers.get(0), crawlerSemaphores.get(0),
            builderSemaphores.get(0));
        Crawler crawler3 = new Crawler(queues.get(2), tree, this.buffers.get(1), crawlerSemaphores.get(1), 
            builderSemaphores.get(1));
        Crawler crawler4 = new Crawler(queues.get(3), tree, this.buffers.get(1), crawlerSemaphores.get(1),
            builderSemaphores.get(1));
        Crawler crawler5 = new Crawler(queues.get(4), tree, this.buffers.get(2), crawlerSemaphores.get(2),
            builderSemaphores.get(2));
        Crawler crawler6 = new Crawler(queues.get(5), tree, this.buffers.get(2), crawlerSemaphores.get(2),
            builderSemaphores.get(2));

        IndexBuilder builder1 = new IndexBuilder(tree, this.buffers.get(0), crawlerSemaphores.get(0),
            builderSemaphores.get(0));
        IndexBuilder builder2 = new IndexBuilder(tree, this.buffers.get(1), crawlerSemaphores.get(1),
            builderSemaphores.get(1));
        IndexBuilder builder3 = new IndexBuilder(tree, this.buffers.get(2), crawlerSemaphores.get(2),
            builderSemaphores.get(2));
        builders.add(builder1);
        builders.add(builder2);
        builders.add(builder3);

        Thread stats = new StatsWriter(tree, queues, this.buffers);

        // Add all threads to a list
        threads.add(crawler1);
        threads.add(crawler2);
        threads.add(crawler3);
        threads.add(crawler4);
        threads.add(crawler5);
        threads.add(crawler6);
        threads.add(builder1);
        threads.add(builder2);
        threads.add(builder3);
        threads.add(stats);

        // Start all threads
        crawler1.start();
        crawler2.start();
        crawler3.start();
        crawler4.start();
        crawler5.start();
        crawler6.start();

        // Commented out ib thread start() so that the program will terminate after all crawler threads have
        // returned.
        // If the ib thread is still running, the program will not terminate.
        builder1.start();
        builder2.start();
        builder3.start();

        stats.start();

        try {
            crawler1.join();
            logger.info(String.format("crawler %d joined...............................", crawler1.getId()));

            crawler2.join();
            logger.info(String.format("crawler %d joined...............................", crawler2.getId()));

            crawler3.join();
            logger.info(String.format("crawler %d joined...............................", crawler3.getId()));

            crawler4.join();
            logger.info(String.format("crawler %d joined...............................", crawler4.getId()));

            crawler5.join();
            logger.info(String.format("crawler %d joined...............................", crawler5.getId()));

            crawler6.join();
            logger.info(String.format("crawler %d joined...............................", crawler6.getId()));
        } catch (InterruptedException e) {
            logger.info("App starting to terminate..................");
            threads.forEach(thread -> thread.interrupt());
            threads.forEach(thread -> {
                try {
                    thread.join();
                } catch (InterruptedException er) {
                    er.printStackTrace();
                }
            });
            logger.info("App exiting...................");
        }

        return null;
    }
    
    public void initialise() {
        Runtime.getRuntime().addShutdownHook(new Cleaner(this.tree, this.buffers, this.queues, this.builders));

        // The following 2 line removes log from the following 2 sources.
        Logger.getLogger("com.gargoylesoftware").setLevel(Level.OFF);
        Logger.getLogger("org.apache.commons.httpclient").setLevel(Level.OFF);

        try {
            // Configure logger to write to external log file
            // TODO: Should this be removed? As the log file can be very large if run for the whole day.
            FileHandler handler = new FileHandler("./log.txt");
            SimpleFormatter formatter = new SimpleFormatter();
            handler.setFormatter(formatter);
            Logger.getLogger("").addHandler(handler);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Return a list with n sempahores for crawlers
    public List<Semaphore> getCrawlerSemaphores(int n) {
        List<Semaphore> list = new ArrayList<>();

        IntStream.range(0, n).forEach(i -> {
            list.add(new Semaphore(BUFFER_SIZE));
        });

        return list;
    }

    // Return a list with n sempahores for builders
    public List<Semaphore> getBuilderSempahores(int n) {
        List<Semaphore> list = new ArrayList<>();

        IntStream.range(0, n).forEach(i -> {
            list.add(new Semaphore(0));
        });

        return list;
    }

    // Read urls from seed file.
    public List<Seed> getURLSeeds() {
        File inputFile = new File(App.inputFileName);
        List<Seed> seeds = new LinkedList<>();

        try {
            BufferedReader bufReader = new BufferedReader(new FileReader(inputFile));
            bufReader.lines().forEach(url -> seeds.add(new Seed("", url)));
            bufReader.close();
        } catch (FileNotFoundException e) {
            logger.severe("Input file cannot be found!");
            System.exit(1);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return seeds;
    }

    // Split the given url list into the specified number of subLists
    // Condition: number of urls in list given >= num of sublists
    public List<List<Seed>> splitList(List<Seed> list, int numSubLists) {
        int portionSize = list.size() / numSubLists;

        List<List<Seed>> result = new LinkedList<>();
        List<Seed> temp = new LinkedList<>();
        int count = 0;
        int currNumSubLists = 0;

        for (Seed seed : list) {
            temp.add(seed);
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

    // Parse input parameters
    public static void parseAndSetParameters(String[] args) {
        try {
            for (int i = 0; i < args.length; i += 2) {
                String flag = args[i];
                String argument = args[i+1];

                if ("-time".equals(flag)) {
                    String hours = argument;
                    Matcher m = Pattern.compile("^\\d+").matcher(hours);
                    if (m.find()) {
                        App.runtime = Integer.parseInt(m.group());
                    } else {
                        throw new IllegalArgumentException("Hours given is not in correct format!");                      
                    }                
                } else if ("-input".equals(flag)) {
                    App.inputFileName = argument;                    
                } else if ("-output".equals(flag)) {
                    App.outputFileName = argument;
                } else if ("-storedPageNum".equals(flag)) {
                    App.numPagesToStore = Integer.parseInt(argument);
                } else {
                    throw new IllegalArgumentException();                      
                }
            }
        } catch (Exception e) {
            System.out.println("Invalid parameters!!");
            System.out.println(e.getMessage());
            e.printStackTrace();            
            System.exit(1);
        }
    }

    public static void main(String[] args) {        
        parseAndSetParameters(args);
        
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            executor.invokeAll(Arrays.asList(new App()), App.runtime, TimeUnit.HOURS); 
            executor.shutdown();
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("Program exiting.............");
    }
}
