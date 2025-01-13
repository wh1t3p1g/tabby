package tabby.analysis.switcher;

import lombok.Getter;
import lombok.Setter;
import soot.Local;
import soot.SootField;
import soot.Value;
import soot.jimple.*;
import tabby.analysis.container.ValueContainer;
import tabby.analysis.data.Context;
import tabby.analysis.data.SimpleObject;
import tabby.common.utils.SemanticUtils;

import java.util.HashSet;
import java.util.List;

/**
 * @author wh1t3P1g
 * @since 2022/1/8
 */
@Getter
@Setter
public abstract class ValueSwitcher extends AbstractJimpleValueSwitch {
    protected Context context;
    protected ValueContainer container;
    protected boolean ifNotExistOrNew = false;
    protected boolean isPrimTypeNeedToCreate = true; // 默认开启prim类型的分析
    protected boolean isAllowDuplicationCreation = false;
    protected boolean forceNew = false;
    protected SimpleObject leftObj = null;
    protected boolean isArray = false;

    public abstract void accept(Context context);

    /**
     * a
     * return obj
     *
     * @param v
     */
    @Override
    public void caseLocal(Local v) {
        SimpleObject obj = null;
        if (!forceNew) {
            obj = container.getObject(v.getName());
        }

        if (obj == null && ifNotExistOrNew
                && (isPrimTypeNeedToCreate || SemanticUtils.isNecessaryType(v))) {
            obj = SimpleObject.makeLocalObject(v, isArray, container);
        }

        setResult(obj);
    }

    /**
     * Class.a
     * return obj
     *
     * @param v
     */
    @Override
    public void caseStaticFieldRef(StaticFieldRef v) {
        SimpleObject obj = null;
        SootField field = v.getField();
        if (field == null) return;

        if (!forceNew) {
            obj = container.getObject(v.toString());
        }

        if (obj == null && ifNotExistOrNew
                && (isPrimTypeNeedToCreate || SemanticUtils.isNecessaryType(v))) {
            obj = SimpleObject.makeStaticFieldObject(v, field, isArray, isAllowDuplicationCreation, container);
        }

        setResult(obj);
    }

    /**
     * a.b
     * return obj
     *
     * @param v
     */
    @Override
    public void caseInstanceFieldRef(InstanceFieldRef v) {
        SimpleObject obj = null;

        if (!forceNew) {
            obj = container.getObject(v);
        }

        if (obj != null && container.isNull(obj) && !isAllowDuplicationCreation) {
            // 当前获取的对象是右值，左值的话isAllowDuplicationCreation=true
            // 此时右值存在且为null，说明之前已经生成过一次了
            // 那么后续不再进行新建操作
            obj = SimpleObject.makeNullObject(container);
        }

        if (obj == null && ifNotExistOrNew
                && (isPrimTypeNeedToCreate || SemanticUtils.isNecessaryType(v))) {
            obj = SimpleObject.makeInstanceFieldObject(v, isArray, isAllowDuplicationCreation, container);
        }

        setResult(obj);
    }

    /**
     * a[]
     * a[][]
     * return obj
     *
     * @param v
     */
    @Override
    public void caseArrayRef(ArrayRef v) {
        SimpleObject obj = null;
        // check base value
        Value base = v.getBase();
        if (base == null) return;

        if (!forceNew) {
            obj = container.getObject(base);
        }

        if (obj == null && ifNotExistOrNew
                && (isPrimTypeNeedToCreate || SemanticUtils.isNecessaryType(v))) {
            setResult(null);
            setForceNew(true);
            isArray = true;
            base.apply(this);
            isArray = false;
            setForceNew(false);
        } else {
            setResult(obj);
        }
    }

    /**
     * 静态变量
     * int、double、float、long、Class、String、Null
     * return obj
     *
     * @param v
     */
    public void caseConstant(Constant v) {
        if (v instanceof StringConstant || v instanceof ClassConstant || v instanceof NullConstant) {
            setResult(SimpleObject.makeConstantObject(v, container));
        }
    }

    /**
     * check array size
     * 当isPrimTypeNeedToCreate=true时才准确
     *
     * @param sizes
     */
    public void checkArraySize(List<Value> sizes) {
        for (Value size : sizes) {
            if (!context.isContainsOutOfMemOptions()) {
                SimpleObject obj = null;
                if (size instanceof Local || size instanceof StaticFieldRef) {
                    obj = container.getObject(size);
                } else if (size instanceof InstanceFieldRef || size instanceof ArrayRef) {
                    obj = container.getOrAdd(size, false);
                }

                if (obj != null && container.isPolluted(obj, new HashSet<>())) {
                    context.setContainsOutOfMemOptions(true);
                    break;
                }
            }
        }
    }

    @Override
    public void caseThisRef(ThisRef v) {
        // do nothing
    }

    @Override
    public void caseParameterRef(ParameterRef v) {
        // do nothing
    }

    /**
     * 简单的变量处理器
     * 用于处理Local、StaticFieldRef、InstanceFieldRef、ArrayRef
     * Context.getOrAdd
     * 不关注
     */
    public static class ValueParser extends ValueSwitcher {

        public ValueParser(ValueContainer container, boolean ifNotExistOrNew) {
            this.container = container;
            this.ifNotExistOrNew = ifNotExistOrNew;
        }

        @Override
        public void accept(Context context) {
            this.container = context.getContainer();
        }

        @Override
        public void defaultCase(Object v) {
            if (v instanceof Constant) {
                caseConstant((Constant) v);
            }
        }
    }
}
