package tabby.analysis.data;

import java.util.HashSet;
import java.util.Set;

/**
 * @author wh1t3P1g
 * @since 2022/1/27
 */

public class MultiObject extends SimpleObject {
    private Set<SimpleObject> objects = new HashSet<>();

    public void addObject(SimpleObject object) {
        if (object == null) return;

        if (objects == null) {
            objects = new HashSet<>();
        }
        if (object instanceof MultiObject) {
            objects.addAll(((MultiObject) object).getObjects());
        } else {
            objects.add(object);
        }
    }

    public void addObjects(Set<SimpleObject> objs) {
        objects.addAll(objs);
    }

    public void addObjects(MultiObject objs) {
        if (objs == null || objs.size() == 0) return;
        objects.addAll(objs.getObjects());
    }

    public int size() {
        if (objects == null) return 0;
        return objects.size();
    }

    public Set<SimpleObject> getObjects() {
        return objects;
    }

    public SimpleObject getFirstObject() {
        if (objects == null || objects.size() == 0) return null;

        return (SimpleObject) objects.toArray()[0];
    }

    public String getName() {
        SimpleObject obj = getFirstObject();
        if (obj == null) {
            return "";
        } else {
            return obj.getName();
        }
    }

    public Set<String> getAllHandles() {
        Set<String> handles = new HashSet<>();
        if (objects == null || objects.size() == 0) return handles;
        for (SimpleObject obj : objects) {
            handles.addAll(obj.getHandle());
        }
        return handles;
    }

    public void setHandles(Set<String> handles) {
        if (objects == null || objects.size() == 0) return;

        for (SimpleObject obj : objects) {
            obj.getHandle().clear();
            obj.getHandle().addAll(handles);
        }
    }

    public Set<String> getTypes() {
        Set<String> types = new HashSet<>();
        if (objects == null || objects.size() == 0) return types;

        for (SimpleObject obj : objects) {
            if (obj != null) {
                types.add(obj.getType());
            }
        }
        return types;
    }

    public String getType() {
        Set<String> types = new HashSet<>();
        if (objects == null || objects.size() == 0) return null;

        for (SimpleObject obj : objects) {
            if (obj != null) {
                types.add(obj.getType());
            }
        }
        if (types.size() > 0) {
            return (String) types.toArray()[0];
        } else {
            return null;
        }
    }

    @Override
    public Set<String> getHandle() {
        Set<String> handles = new HashSet<>();
        for (SimpleObject obj : objects) {
            if (obj == null) continue;
            handles.addAll(obj.getHandle());
        }
        return handles;
    }

    @Override
    public String toString() {
        return "MultiObject{" +
                "objects=" + objects +
                '}';
    }

    @Override
    public boolean isNotEmpty() {
        return objects != null && objects.size() > 0;
    }
}
