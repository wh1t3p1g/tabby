package tabby.util;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;

/**
 * @author wh1t3P1g
 * @since 2020/11/5
 */
public class CSVUtils {

    public static void save(String path, String[] headers, Collection<List<String>> classRefs) throws IOException {
        BufferedWriter writer = Files.newBufferedWriter(Paths.get(path));

        try(CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(headers))){
            csvPrinter.printRecords(classRefs);
        }
    }
}
