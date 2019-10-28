package pl.kelog;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Stream;

class Main {
    
    private static final String BASE_URL = "http://www.ustron.luteranie.pl/";
    private static final String OUTPUT_FOLDER = "/tmp/pictures";
    
    /* This shared hosting is allergic to concurrent and repeated requests */
    private static final int THREAD_COUNT = 1;
    private static final long DELAY = 100;
    
    public static void main(String[] args) throws Exception {
        ensureOutputFolderIsPresent();
        
        new ForkJoinPool(THREAD_COUNT).submit(
                createMainTask()
        ).get();
    }
    
    private static void ensureOutputFolderIsPresent() {
        if (!new File(OUTPUT_FOLDER).isDirectory()) {
            throw new AssertionError("Output folder " + OUTPUT_FOLDER + " not found.");
        }
    }
    
    private static Runnable createMainTask() {
        return () -> findSubpages()
                .flatMap(Main::findNewsPages)
                .flatMap(Main::findPictureLinks)
                .forEach(Main::downloadFileAtUrl);
    }
    
    private static Stream<String> findSubpages() {
        List<String> subpages = new ArrayList<>();
        subpages.add(BASE_URL);
        
        String currentPageUrl = BASE_URL;
        while (true) {
            Elements nextLink = connect(currentPageUrl).select(".pNext");
            if (nextLink.isEmpty()) {
                break;
            }
            currentPageUrl = BASE_URL + nextLink.attr("href");
            subpages.add(currentPageUrl);
            System.out.println("Found subpage: " + currentPageUrl);
        }
        
        return subpages.parallelStream();
    }
    
    private static Stream<String> findNewsPages(String subpageUrl) {
        return connect(subpageUrl)
                .select("h2 > a").stream()
                .map(e -> BASE_URL + "/" + e.attr("href"))
                .peek(url -> System.out.println("Found news page: " + url));
    }
    
    private static Stream<String> findPictureLinks(String newsPage) {
        return connect(newsPage)
                .select("ul.imagesList > li > a").parallelStream()
                .map(e -> BASE_URL + e.attr("href"))
                .peek(url -> System.out.println("Found picture: " + url));
    }
    
    private static void downloadFileAtUrl(String url) {
        System.out.print("Downloading " + url + "...");
        try {
            FileUtils.copyURLToFile(new URL(url), new File(OUTPUT_FOLDER + File.separator + filenameFor(url)));
            System.out.println(" done.");
        } catch (IOException e) {
            e.printStackTrace();
        }
        sleep(DELAY);
    }
    
    private static String filenameFor(String url) {
        return UUID.randomUUID().toString() + "." + FilenameUtils.getExtension(url);
    }
    
    private static Document connect(String url) {
        sleep(DELAY);
        try {
            return Jsoup.connect(url).get();
        } catch (Exception e) {
            System.err.println("Connection error, ignoring.");
            return Jsoup.parse("<html></html>");
        }
    }
    
    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }
}
