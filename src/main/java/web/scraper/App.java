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
    public static final int BUFFER_SIZE = 1;
    public static final int NUM_BUFFERS = 2;
    public static final int NUM_CRAWLERS = NUM_BUFFERS * 2;    
    public static int runtime;
    public static int numPagesToStore;
    public static String inputFileName;
    public static String outputFileName;

    private Logger logger;
    private IndexURLTree tree;
    private List<List<Data>> buffers;
    private List<List<Seed>> queues; // synchronised in the App constructor
    private List<Crawler> crawlers;
    private List<IndexBuilder> builders;
    private StatsWriter statsWriter;
    private List<Thread> threads;

    public App() {
        this.logger = Logger.getLogger("App");
        this.tree = new IndexURLTree(outputFileName, App.numPagesToStore);
        this.buffers = new LinkedList<>();
        IntStream.range(0, NUM_BUFFERS).forEach(x -> buffers.add(Collections.synchronizedList(new LinkedList<>())));
        this.queues = new LinkedList<>();
        this.threads = new LinkedList<>();
    }

    public Void call() {
        logger.info("Starting........ =D");
        initialise();

        List<Seed> seeds = getURLSeeds();
        this.queues.addAll(splitList(seeds, NUM_CRAWLERS));


        List<Semaphore> crawlerSemaphores = getCrawlerSemaphores();
        List<Semaphore> builderSemaphores = getBuilderSempahores();


        // Create all threads
        this.crawlers = getCrawlers(crawlerSemaphores, builderSemaphores);
        this.builders = getBuilders(crawlerSemaphores, builderSemaphores);
        this.statsWriter = new StatsWriter(this.tree, this.queues, this.buffers);


        // Add all threads
        this.threads.addAll(crawlers);
        this.threads.addAll(builders);
        this.threads.add(statsWriter);

        // Start all threads
        this.threads.forEach(thread -> thread.start());

        
        try {
            for (int i = 0; i < NUM_CRAWLERS; i++) {
                Crawler crawler = this.crawlers.get(i);
                crawler.join();
                logger.info(String.format("Crawler %d joined...............................", crawler.getId()));
            }


            this.builders.forEach(builder -> builder.interrupt());

            for (int i = 0; i < NUM_BUFFERS; i++) {
                IndexBuilder builder = this.builders.get(i);
                builder.join();
                logger.info(String.format("Builder %d joined...............................", builder.getId()));
            }

            this.statsWriter.interrupt();
            this.statsWriter.join();
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
        }
        
        logger.info("App exiting...................");
        return null;
    }
    
    public void initialise() {
        Runtime.getRuntime().addShutdownHook(new Cleaner(this));

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

    public List<Crawler> getCrawlers(List<Semaphore> crawlerSemaphores, List<Semaphore> builderSemaphores) {
        List<Crawler> crawlers = new ArrayList<>();

        if (this.queues.size() < NUM_CRAWLERS) {
            this.logger.warning("Number of queues is lesser than the number of crawlers, padding queues with empty queue");
            
            // Pad queues with empty queue
            int difference = NUM_CRAWLERS - this.queues.size();
            IntStream.range(0, difference).forEach(i -> this.queues.add(new LinkedList<>()));
        }

        for (int i = 0; i < NUM_CRAWLERS; i++) {
            crawlers.add(new Crawler(this.queues.get(i), this.tree, this.buffers.get(i/2), crawlerSemaphores.get(i/2),
               builderSemaphores.get(i/2)));
        }

        return crawlers;
    }

    public List<IndexBuilder> getBuilders(List<Semaphore> crawlerSemaphores, List<Semaphore> builderSemaphores) {
        List<IndexBuilder> builders = new ArrayList<>();

        for (int i = 0; i < NUM_BUFFERS; i++) {
            builders.add(new IndexBuilder(this.tree, this.buffers.get(i), crawlerSemaphores.get(i),
                builderSemaphores.get(i)));
        }

        return builders;
    }

    // Return a list with n sempahores for crawlers
    public List<Semaphore> getCrawlerSemaphores() {
        List<Semaphore> list = new ArrayList<>();

        IntStream.range(0, NUM_BUFFERS).forEach(i -> {
            list.add(new Semaphore(BUFFER_SIZE));
        });

        return list;
    }

    // Return a list with n sempahores for builders
    public List<Semaphore> getBuilderSempahores() {
        List<Semaphore> list = new ArrayList<>();

        IntStream.range(0, NUM_BUFFERS).forEach(i -> {
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

    public IndexURLTree getTree() {
        return this.tree;
    }

    public List<List<Data>> getBuffers() {
        return this.buffers; 
    }

    public List<List<Seed>> getQueues() {
        return this.queues;
    }

    public List<IndexBuilder> getBuilders() {
        return this.builders;
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
