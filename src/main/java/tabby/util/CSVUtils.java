package tabby.util;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.supercsv.cellprocessor.FmtBool;
import org.supercsv.cellprocessor.Optional;
import org.supercsv.cellprocessor.ift.CellProcessor;

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

    public static CellProcessor[] getClassRefProcessor(){
        return new CellProcessor[] { new Optional(), // uuid
                  			new Optional(), // name
                  			new Optional(), // superClass
                  			new Optional(), // interfaces lists
                  			new Optional(new FmtBool("true", "false")), // isInterface
                  			new Optional(new FmtBool("true", "false")), // hasSuperClass
                  			new Optional(new FmtBool("true", "false")), // hasInterfaces
                  			new Optional(), // fields
                  		};
    }

    public static void save(String path, String[] headers, Collection<List<String>> classRefs) throws IOException {
        BufferedWriter writer = Files.newBufferedWriter(Paths.get(path));

        try(CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(headers))){
            csvPrinter.printRecords(classRefs);
        }
    }
}
