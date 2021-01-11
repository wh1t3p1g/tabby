package tabby.db.converter;

import org.supercsv.cellprocessor.CellProcessorAdaptor;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.util.CsvContext;
import tabby.db.bean.ref.ClassReference;

/**
 * @author wh1t3P1g
 * @since 2021/1/11
 */
public class ClassRefCellProcessor extends CellProcessorAdaptor {

    public ClassRefCellProcessor() {
        super();
    }

    public ClassRefCellProcessor(CellProcessor next) {
        // this constructor allows other processors to be chained after ParseDay
        super(next);
    }

    @Override
    public Object execute(Object o, CsvContext csvContext) {
        if(o == null){
            return "";
        }
        ClassReference ref = (ClassReference) o;
        return ref.getId();
    }
}
