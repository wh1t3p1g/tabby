package tabby.db.converter;

import org.supercsv.cellprocessor.CellProcessorAdaptor;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.util.CsvContext;
import tabby.db.bean.ref.MethodReference;

/**
 * @author wh1t3P1g
 * @since 2021/1/11
 */
public class MethodRefCellProcessor extends CellProcessorAdaptor {

    public MethodRefCellProcessor() {
        super();
    }

    public MethodRefCellProcessor(CellProcessor next) {
        // this constructor allows other processors to be chained after ParseDay
        super(next);
    }

    @Override
    public Object execute(Object o, CsvContext csvContext) {
        if(o == null){
            return "";
        }
        MethodReference ref = (MethodReference) o;
        return ref.getId();
    }
}
