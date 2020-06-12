## WebCrawler

* Потокобезопасный класс `WebCrawler` рекурсивно обходит сайты.
* Класс `WebCrawler` имеет конструктор
    ```
    public WebCrawler(Downloader downloader, int downloaders, int extractors, int perHost)
    ```             
    * `downloader` позволяет скачивать страницы и извлекать из них ссылки;
    * `downloaders` — максимальное число одновременно загружаемых страниц;
    * `extractors` — максимальное число страниц, из которых извлекаются ссылки;
    * `perHost` — максимальное число страниц, одновременно загружаемых c одного хоста.
* Класс `WebCrawler` реализует интерфейс `Crawler`
    ```
    public interface Crawler extends AutoCloseable {
        Result download(String url, int depth);
    
        void close();
    }
    ```                 
    * Метод `download` рекурсивно обходит страницы, начиная с указанного URL на указанную глубину и возвращает список загруженных страниц и файлов. 
        Например, если глубина равна 1, то будет загружена только указанная страница. Если глубина равна 2, то указанная страница и те страницы и файлы, на которые она ссылается и так далее. Этот метод может вызываться параллельно в нескольких потоках.
    * Загрузка и обработка страниц (извлечение ссылок) выполняется максимально параллельно, с учетом ограничений на число одновременно загружаемых страниц (в том числе с одного хоста) и страниц, с которых загружаются ссылки.
    * Для распараллеливания создаётся до `downloaders` + `extractors` вспомогательных потоков.
    * Ссылки из одной и той же страницы в рамках одного обхода (`download`) загружаются/извлекается не более одного раза.
    * Метод `close` завершает все вспомогательные потоки.
* Для загрузки страниц применяется `Downloader`, передаваемый первым аргументом конструктора.
    ```
    public interface Downloader {
        public Document download(final String url) throws IOException;
    }
    ```                    
    * Метод `download` загружает документ по его адресу (URL).
    * Документ позволяет получить ссылки по загруженной странице:
        ```
        public interface Document {
            List<String> extractLinks() throws IOException;
        }
        ```      
    * Ссылки, возвращаемые документом являются абсолютными и имеют схему http или https.
* Есть метод main, позволяющий запустить обход из командной строки
    * Командная строка
        ```
        WebCrawler url [depth [downloads [extractors [perHost]]]]
        ```    
    * Для загрузки страниц используется реализация `CachingDownloader` из тестов.

* Для того, чтобы протестировать программу:
   * Скачайте
      * тесты
          * [info.kgeorgiy.java.advanced.base.jar](artifacts/info.kgeorgiy.java.advanced.base.jar)
          * [info.kgeorgiy.java.advanced.crawler.jar](artifacts/info.kgeorgiy.java.advanced.crawler.jar)
      * и библиотеки к ним:
          * [junit-4.11.jar](lib/junit-4.11.jar)
          * [hamcrest-core-1.3.jar](lib/hamcrest-core-1.3.jar)
          * [jsoup-1.8.1.jar](lib/jsoup-1.8.1.jar)
          * [quickcheck-0.6.jar](lib/quickcheck-0.6.jar)
   * Откомпилируйте программу
   * Протестируйте программу
      * Текущая директория должна:
         * содержать все скачанные `.jar` файлы;
         * содержать скомпилированные классы;
         * __не__ содержать скомпилированные самостоятельно тесты.
      * ```java -cp . -p . -m info.kgeorgiy.java.advanced.crawler advanced ru.ifmo.rain.shaposhnikov.crawler.WebCrawler```