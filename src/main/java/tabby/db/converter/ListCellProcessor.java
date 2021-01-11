package tabby.db.converter;

import org.supercsv.cellprocessor.CellProcessorAdaptor;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.util.CsvContext;
import tabby.config.GlobalConfiguration;

/**
 * @author wh1t3P1g
 * @since 2021/1/11
 */
public class ListCellProcessor extends CellProcessorAdaptor {

    public ListCellProcessor() {
        super();
    }

    public ListCellProcessor(CellProcessor next) {
        // this constructor allows other processors to be chained after ParseDay
        super(next);
    }

    @Override
    public Object execute(Object o, CsvContext csvContext) {
        if(o == null){
            return "[]";
        }
        return GlobalConfiguration.GSON.toJson(o);
    }
}
