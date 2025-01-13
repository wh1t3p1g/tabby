package tabby.analysis.switcher.pta;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import soot.*;
import soot.jimple.*;
import tabby.analysis.ActionWorker;
import tabby.analysis.SimpleTypeAnalysis;
import tabby.analysis.data.Context;
import tabby.analysis.data.*;
import tabby.analysis.helper.SemanticHelper;
import tabby.analysis.switcher.ValueSwitcher;
import tabby.common.bean.ref.ClassReference;
import tabby.common.bean.ref.MethodReference;
import tabby.common.utils.PositionUtils;
import tabby.common.utils.SemanticUtils;
import tabby.config.GlobalConfiguration;
import tabby.core.collector.ClassInfoCollector;

import java.util.*;

/**
 * 处理右职
 *
 * @author wh1t3p1g
 * @since 2021/11/26
 */
@Getter
@Setter
@Slf4j
public class TValueSwitcher extends ValueSwitcher {

    public TValueSwitcher(Context context) {
        this.context = context;
        this.container = context.getContainer();
        this.ifNotExistOrNew = true;
    }

    @Override
    public void accept(Context context) {
        this.context = context;
        this.container = context.getContainer();
    }

    /**
     * a = b;
     * return obj
     *
     * @param v
     */
    @Override
    public void caseLocal(Local v) {
        // Local 不可能凭空出现，所以不add
        // 其他field、array等 允许新建
        SimpleObject rObj = container.getObject(v.getName());
        setResult(rObj);
    }

    /**
     * a = (A) value;
     * 只返回当前value，所以只做转发
     *
     * @param v
     */
    @Override
    public void caseCastExpr(CastExpr v) {
        Value value = v.getOp();
        value.apply(this);
    }

    /**
     * a = new xxx
     * return value
     *
     * @param v
     */
    @Override
    public void caseNewExpr(NewExpr v) {
        Type type = v.getType();
        String classname = type.toQuotedString();
        ClassReference classRef = this.container.getDataContainer().getClassRefByName(classname, true);
        if (classRef == null) {
            ClassInfoCollector.collectRuntimeForSingleClazz(classname, true, this.container.getDataContainer(), null);
        } else { // check methods
            for (String method : classRef.getMethodSubSignatures()) {
                // check all method is constructed
                MethodReference methodRef = this.container.getDataContainer().getMethodRefBySubSignature(classname, method, true);
                if (methodRef == null) {
                    // try to construct again
                    SootMethod sootMethod = SemanticUtils.getMethod(classname, method);
                    ClassInfoCollector.collectSingleMethodRef(classRef, sootMethod, true, true, this.getContainer().getDataContainer(), null);
                }
            }
        }
        setResult(SimpleValue.makeTransferValue(v.getType()));
    }

    /**
     * a = newarray xxx
     * return value
     *
     * @param v
     */
    @Override
    public void caseNewArrayExpr(NewArrayExpr v) {
        if (isPrimTypeNeedToCreate) {
            checkArraySize(Arrays.asList(v.getSize()));
        }
        setResult(SimpleValue.makeTransferValue(v.getType()));
    }

    /**
     * a = newmultiarray xxx
     * return value
     *
     * @param v
     */
    @Override
    public void caseNewMultiArrayExpr(NewMultiArrayExpr v) {
        if (isPrimTypeNeedToCreate) {
            checkArraySize(v.getSizes());
        }
        setResult(SimpleValue.makeTransferValue(v.getType()));
    }

    @Override
    public void caseAddExpr(AddExpr v) {
        caseMathExpr(v);
    }

    @Override
    public void caseSubExpr(SubExpr v) {
        caseMathExpr(v);
    }

    public void caseMathExpr(BinopExpr v) {
        MultiObject ret = new MultiObject();
        for (ValueBox box : v.getUseBoxes()) {
            Value value = box.getValue();
            ret.addObject(container.getOrAdd(value, false));
        }
        setResult(ret);
    }

    /**
     * @param v
     */
    @Override
    public void caseDynamicInvokeExpr(DynamicInvokeExpr v) {
        super.caseDynamicInvokeExpr(v);
    }

    /**
     * 统一返回SimpleObject
     *
     * @param v
     */
    public void caseInvokeExpr(InvokeExpr v) {

        if (v instanceof StaticInvokeExpr && v.getArgCount() == 0 && !GlobalConfiguration.IS_NEED_ANALYSIS_EVERYTHING)
            return; // 不影响后续分析

        if (context.isInterProcedural()) {
            // 提取当前参与的变量
            SimpleObject[] objects = SemanticHelper.extractObjectFromInvokeExpr(v, context.getContainer());
            // 当不存在污染变量时，返回值不影响全局污点传播，所以不分析此种情况的调用
            if (!container.isContainsPollutedObject(objects) && v.getArgCount() == 0 && !GlobalConfiguration.IS_NEED_ANALYSIS_EVERYTHING) {
                return;
            }
            // 存在污染变量时，将递归处理下一个函数
            Set<MethodReference> targets = dispatch(v);
            if (targets.isEmpty()) {
                setResult(simplify(v));
                return;
            }
            // 处理每一个可能的函数
            Type returnType = null;
            boolean isVoidReturnType = false;
            Iterator<MethodReference> iterator = targets.iterator();
            Map<String, Set<String>> actions = new HashMap<>();
            Set<String> returnTypes = new HashSet<>();
            int maxMethodCount = 0;
            while (iterator.hasNext()) {
                MethodReference target = iterator.next();
                if (target == null || "<java.lang.Object: void <init>()>".equals(target.getSignature())) continue;
                if (maxMethodCount > GlobalConfiguration.ALIAS_METHOD_MAX_COUNT) {
                    // 最多处理10个函数
                    break;
                } else {
                    maxMethodCount += 1;
                }
                SootMethod method = target.getMethod();
                if (method == null) continue;

                returnType = method.getReturnType();
                if (!isVoidReturnType && returnType instanceof VoidType) {
                    isVoidReturnType = true;
                }

                if (target.isInitialed() || target.isActionInitialed()) {
                    target.addCallCounter();
                    // 解决循环+swap的情况
                    if (target.isActionContainsSwap() && target.getCallCounter() > 20) continue;
                    SemanticUtils.merge(target.getActions(), actions);
                    continue;
                } else if (target.isDao() || target.isRpc() || target.isMRpc()) {
                    SemanticUtils.applySpecialMethodActions(target);
                    SemanticUtils.merge(target.getActions(), actions);
                    continue;
                }

                boolean flag = false;
                if (method.isPhantom() || method.isAbstract() || method.isNative()
                        || context.isInRecursion(target.getSignature()) || context.isOverMaxDepth()
                        || target.isContainSomeError()
                ) {
                    flag = true;
                } else if (target.isEverTimeout()) {
                    // 放到timeout地方
                    context.setAnalyseTimeout(true);
                } else {
                    try (Context subContext = context.createSubContext(target)) {
                        subContext.setPreObjects(objects);
                        subContext.setCurObjects(new SimpleObject[objects.length]);
                        if (SimpleTypeAnalysis.processMethod(target.getMethod(), subContext) && !subContext.isAnalyseTimeout() && !subContext.isForceStop()) {
                            SemanticUtils.merge(target.getActions(), actions);
                        } else {
                            flag = true;
                        }
                        context.sub(subContext.cost());
                    }
                }

                if (flag) {
                    // 由于没办法获取method body，或递归的情况，这里采取两种策略
                    // 1. 返回值void，则保持当前函数调用者和参数的污点性，不做处理。这里可能存在参数污点丢失的情况
                    // 2. 返回值不为void，则采用模糊处理，返回值污点信息copy函数调用舍和参数的所有污点信息，并将涉及的变量污点信息都统一化
                    // 3. 分析出错的情况
                    if (returnType instanceof VoidType) continue;
                    // 模糊处理
                    SimpleObject retObj = simplify(v);
                    Map<String, Set<String>> temp = new HashMap<>();
                    temp.put("return<s>", retObj.getInvokeTargets().getOrDefault("return", new HashSet<>())); // 标志当前action来源于simplify的结果
                    SemanticUtils.merge(temp, actions);
                }

                returnTypes.addAll(target.getReturnType());
            }


            // apply actions
            container.setObjects(context.getCurObjects());

            ActionWorker worker = new ActionWorker(actions, returnType, container, objects);
            worker.doWork();
            SimpleObject retObj = worker.getRetObj();
            if (!isVoidReturnType) {
                container.applyTypes(retObj, returnTypes); // TODO 需案例测试
            }
            container.setObjects(null);
            setResult(retObj);
        } else {
            setResult(simplify(v));
        }
    }

    /**
     * 简化函数涉及变量的传播
     * 简化处理主要是处理函数调用涉及的对象，如函数调用者，函数参数列表，函数返回值
     * 经过简化处理后，上面涉及的对象position都将统一为同一个列表（除常量外）
     *
     * @param v
     */
    public SimpleObject simplify(InvokeExpr v) {
        List<ValueBox> valueBoxes = v.getUseBoxes();
        Set<Integer> positions = container.collectPositions(valueBoxes); // 获取所有的污点信息
        SootMethod method = SemanticUtils.getMethod(v);

        SimpleObject retObj = SimpleObject.makeInvokeReturnObject(v.toString());
        SimpleValue val = SimpleValue.makeReturnValue(method.getReturnType());
        retObj.getHandle().add(val.getUuid());

        if (positions.size() > 0) {
            int count = 0;
            Value[] values = new Value[v.getArgCount() + 1];
            if (v instanceof InstanceInvokeExpr) {
                Value base = ((InstanceInvokeExpr) v).getBase();
                if (base instanceof PrimType || base instanceof Constant) {
                    values[0] = null;
                } else {
                    values[0] = base;
                    count++;
                }
            }
            int i = 1;
            for (Value value : v.getArgs()) {
                if (value instanceof PrimType || value instanceof Constant) {
                    values[i] = null;
                } else {
                    values[i] = value;
                    count++;
                }
            }
            if (count > 2) {
                for (int t = 0; t < values.length; t++) {
                    if (values[t] != null) {
                        container.updateStatus(values[t], Domain.UNKNOWN, positions, false);
                    }
                }
            }

            for (int pos : positions) {
                if (pos == PositionUtils.NOT_POLLUTED_POSITION) continue;
                retObj.addInvokeTargets("return<s>", PositionUtils.getPosition(pos));
            }
            // return value 传播返回值
            val.setStatus(Domain.UNKNOWN);
            val.setPosition(positions);
        }

        container.addValueToContainer(val, false);
        // 这里不保存当前的obj值，后续仅会用到val值
        // 为什么不直接用val，因为保持处理的统一性
        retObj.setSimplifyInvoke(true);
        return retObj;
    }

    /**
     * 对于interface invoke类型，不能直接去遍历所有的函数，这样会导致递归层数过多的问题
     * 所以这里后续会对dispatch所得的method进行限制
     *
     * @param v
     * @return
     */
    public Set<MethodReference> dispatch(InvokeExpr v) {
        Set<MethodReference> targets = new HashSet<>();
        if (v instanceof StaticInvokeExpr || v instanceof SpecialInvokeExpr) {
            // 处理调用者类型固定的情况，直接取当前的method
            // 相对来说比较简单
            // 包括 静态函数调用，构造函数调用，实例的私有方法调用，实例的父类方法调用
            MethodReference targetMethodRef = context.getDataContainer().getOrAddMethodRef(v);
            targets.add(targetMethodRef);

        } else if (v instanceof InterfaceInvokeExpr || v instanceof VirtualInvokeExpr) {
            // 需要使用alias 和 type 来找到可能的method
            SootMethod method = SemanticUtils.getMethod(v);
            MethodReference targetMethodRef = context.getDataContainer()
                    .getOrAddMethodRef(v.getMethodRef());
            if (targetMethodRef != null && (targetMethodRef.isActionInitialed()
                    || (targetMethodRef.isDao() || targetMethodRef.isRpc() || targetMethodRef.isMRpc()))) {
                targetMethodRef.setMethod(method);
                targets.add(targetMethodRef);
            } else if (method.isPhantom() || method.isAbstract() || method.isNative()) {
                // 如果当前method是可以分析的 就是能获取到body的，那么相信soot的处理，直接采用当前的method
                Value base = ((InstanceInvokeExpr) v).getBase();
                SimpleObject baseObj = container.getObject(base);

                if (baseObj != null) { // 根据当前的baseObj类型获取实现
                    Set<String> types = new HashSet<>();
                    types.add(baseObj.getType());
                    for (String handle : baseObj.getHandle()) {
                        SimpleValue val = container.getValue(handle);
                        if (val != null) {
                            types.addAll(val.getType());
                        }
                    }
                    types.remove("null_type");
//                    types.remove(method.getDeclaringClass().toString());

                    String subSignature = method.getSubSignature();

                    for (String type : types) {
                        MethodReference target = context.getDataContainer().getFirstMethodRefFromChild(type, subSignature);
                        if (target != null) {
                            targets.add(target);
                        }
                    }
                }

//                if(targets.size() == 0 ){ // 这里的随机选取某几个alias函数会导致内容的遗漏，这里还是采用simplify处理
//                    // 这里会导致递归深度非常大，所以要限制alias的内容边
//                    Set<Alias> aliases = targetMethodRef.getChildAliasEdges();
//                    for(Alias alias:aliases){
//                        if(aliases.size() > GlobalConfiguration.ALIAS_METHOD_MAX_COUNT){
//                            break;
//                        }
//                        targets.addAll(alias.getAllTargets());
//                    }
//                }
            } else {
                targets.add(targetMethodRef);
            }

        }

        return targets;
    }

    @Override
    public void defaultCase(Object v) {
        if (v instanceof Constant) {
            caseConstant((Constant) v);
        } else if (v instanceof InvokeExpr) { // 处理右值的函数调用场景
//            SootMethod method = ((InvokeExpr) v).getMethod();

//            if("getAttributeValue".equals(method.getName())){
//                System.out.println("test");
//            }
            caseInvokeExpr((InvokeExpr) v);
        }
    }


}
