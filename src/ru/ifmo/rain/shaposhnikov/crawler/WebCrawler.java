package ru.ifmo.rain.shaposhnikov.crawler;

import info.kgeorgiy.java.advanced.crawler.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Recursively crawl links and download web pages.
 *
 * @author Boris Shaposhnikov
 */
public class WebCrawler implements Crawler {
    /**
     * Default depth of crawling.
     */
    public final static int DEFAULT_DEPTH = 1;
    /**
     * Default maximum thread number for page downloading.
     */
    public final static int DEFAULT_DOWNLOADS = 16;
    /**
     * Default maximum thread number for link extracting.
     */
    public final static int DEFAULT_EXTRACTORS = 16;
    /**
     * Maximum number of pages simultaneously loaded from one host.
     */
    public final static int DEFAULT_PERHOST = 16;
    /**
     * Awaiting time
     */
    private final static int AWAIT_TIME = 3;

    private final Downloader downloader;
    private final ExecutorService downloadService;
    private final ExecutorService extractService;
    private final int perHost;
    private final ConcurrentHashMap<String, HostQueue> hosts;

    /**
     * Creates Web Crawler
     *
     * @param downloader  depth of crawling
     * @param downloaders maximum thread number for page downloading
     * @param extractors  maximum thread number for link extracting
     * @param perHost     maximum number of pages simultaneously loaded from one host
     */
    public WebCrawler(final Downloader downloader, final int downloaders,
                      final int extractors, final int perHost) {
        this.downloader = downloader;
        this.downloadService = Executors.newFixedThreadPool(downloaders);
        this.extractService = Executors.newFixedThreadPool(extractors);
        this.perHost = perHost;
        this.hosts = new ConcurrentHashMap<>();
    }

    @Override
    public Result download(final String url, final int depth) {
        final Set<String> downloaded = ConcurrentHashMap.newKeySet();
        final Map<String, IOException> errors = new ConcurrentHashMap<>();

        final Queue<String> nextLayer = new ConcurrentLinkedQueue<>();
        nextLayer.add(url);

        final Phaser phaser = new Phaser(1);
        for (int i = 0; i < depth; i++) {
            final List<String> currentLayer = List.copyOf(nextLayer);
            nextLayer.clear();
            final int finalI = i;
            currentLayer.stream()
                    .filter(downloaded::add)
                    .forEach(urlToDownload -> downloadTask(urlToDownload, depth - finalI, nextLayer, errors, phaser));
            phaser.arriveAndAwaitAdvance();
        }
        downloaded.removeAll(errors.keySet());
        return new Result(List.copyOf(downloaded), errors);
    }

    private void extractorTask(final Document document, final Phaser phaser, final Queue<String> nextLayer) {
        phaser.register();
        extractService.submit(() -> {
            try {
                nextLayer.addAll(document.extractLinks());
            } catch (final IOException ignored) {
            } finally {
                phaser.arriveAndDeregister();
            }
        });
    }

    private void downloadTask(final String url, final int depth, final Queue<String> nextLayer, final Map<String, IOException> errors,
                              final Phaser phaser) {
        try {
            final HostQueue hostQueue = hosts.computeIfAbsent(URLUtils.getHost(url), x -> new HostQueue());
            phaser.register();
            hostQueue.add(() -> {
                try {
                    final Document document = downloader.download(url);
                    if (depth > 1) {
                        extractorTask(document, phaser, nextLayer);
                    }
                } catch (final IOException e) {
                    errors.put(url, e);
                } finally {
                    phaser.arriveAndDeregister();
                    hostQueue.next();
                }
            });
        } catch (final MalformedURLException e) {
            errors.put(url, e);
        }
    }

    private void await(final ExecutorService service) {
        try {
            service.awaitTermination(AWAIT_TIME, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            System.err.println("Service was interrupted: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        downloadService.shutdown();
        extractService.shutdown();
        await(downloadService);
        await(extractService);
    }

    private static int parseIntegerArgument(final String[] args, final int ind,
                                            final int defaultValue, final String meaning) {
        if (ind >= args.length) {
            return defaultValue;
        }
        Objects.requireNonNull(args[ind]);
        final int value = Integer.parseInt(args[ind]);
        if (value < 1) {
            System.err.println(String.format("%d argument(%s) must be a positive number",
                    ind + 1, meaning));
        }
        return value;
    }

    /**
     * Main function for running program.
     *
     * @param args url [depth [downloads [extractors [perHost]]]]
     */
    public static void main(final String[] args) {
        Objects.requireNonNull(args);

        if (args.length == 0) {
            System.err.println("1 to 5 arguments expected");
        }

        final String url = args[0];
        final int depth = parseIntegerArgument(args, 1, DEFAULT_DEPTH, "depth");
        final int downloads = parseIntegerArgument(args, 2, DEFAULT_DOWNLOADS, "downloads");
        final int extractors = parseIntegerArgument(args, 3, DEFAULT_EXTRACTORS, "extractors");
        final int perHost = parseIntegerArgument(args, 4, DEFAULT_PERHOST, "perHost");

        try (final Crawler crawler = new WebCrawler(new CachingDownloader(), downloads, extractors, perHost)) {
            final Result result = crawler.download(url, depth);

            final List<String> downloaded = result.getDownloaded();
            final Map<String, IOException> errors = result.getErrors();

            System.out.println("Successfully downloaded: " + downloaded.size());
            downloaded.forEach(System.out::println);

            System.out.println(errors.size() + "errors occurred while downloading:");
            errors.forEach((u, e) -> System.out.println(String.format("    url: %s\n    %s", u, e.getMessage())));
        } catch (final IOException e) {
            System.err.println("Error during downloading: " + e.getMessage());
        }
    }

    private class HostQueue {
        private int downloading;
        private final Queue<Runnable> queue = new ArrayDeque<>();

        public synchronized void add(final Runnable runnable) {
            if (downloading < perHost) {
                downloading++;
                downloadService.submit(runnable);
            } else {
                queue.add(runnable);
            }
        }

        public synchronized void next() {
            final Runnable runnable = queue.poll();
            if (runnable == null) {
                downloading--;
            } else {
                downloadService.submit(runnable);
            }
        }
    }
}
