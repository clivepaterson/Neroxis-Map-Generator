package com.faforever.neroxis.generator.graph.io;

import com.faforever.neroxis.generator.graph.domain.MapMaskMethodVertex;
import com.faforever.neroxis.generator.graph.domain.MaskConstructorVertex;
import com.faforever.neroxis.generator.graph.domain.MaskGraphVertex;
import com.faforever.neroxis.generator.graph.domain.MaskMethodEdge;
import com.faforever.neroxis.generator.graph.domain.MaskMethodVertex;
import com.faforever.neroxis.mask.MapMaskMethods;
import com.faforever.neroxis.mask.Mask;
import com.faforever.neroxis.util.DebugUtil;
import com.faforever.neroxis.util.MaskReflectUtil;
import org.jgrapht.Graph;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.AttributeType;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.dot.DOTExporter;
import org.jgrapht.nio.dot.DOTImporter;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.Map;

public class GraphSerializationUtil {
    private static final DOTImporter<MaskGraphVertex<?>, MaskMethodEdge> IMPORTER = new DOTImporter<>();
    private static final DOTExporter<MaskGraphVertex<?>, MaskMethodEdge> EXPORTER = new DOTExporter<>();
    private static final String PARAMETER_VALUE_PREFIX = "paramValue";
    private static final String PARAMETER_CLASS_PREFIX = "paramClass";
    private static final String VERTEX_CLASS_ATTRIBUTE = "class";
    private static final String MASK_CLASS_ATTRIBUTE = "maskClass";
    private static final String EXECUTABLE_ATTRIBUTE = "executable";
    private static final String PARAMETER_COUNT_ATTRIBUTE = "parameterCount";
    private static final String PARAMETER_NAME_ATTRIBUTE = "parameterName";
    private static final String RESULT_NAME_ATTRIBUTE = "resultName";
    private static final String IDENTIFIER_NAME_ATTRIBUTE = "identifier";

    static {
        EXPORTER.setVertexAttributeProvider(GraphSerializationUtil::getAttributeMap);
        EXPORTER.setEdgeAttributeProvider(GraphSerializationUtil::getAttributeMap);
        IMPORTER.setVertexWithAttributesFactory(((id, attributeMap) -> getMaskGraphVertexFromAttributes(attributeMap)));
        IMPORTER.setEdgeWithAttributesFactory((GraphSerializationUtil::getMaskMethodEdgeFromAttributes));
    }

    private static MaskGraphVertex<?> getMaskGraphVertexFromAttributes(Map<String, Attribute> attributeMap) {
        if (attributeMap.isEmpty()) {
            return new MaskConstructorVertex(null);
        }
        try {
            Class<? extends MaskGraphVertex<?>> vertexClass = (Class<? extends MaskGraphVertex<?>>) getClassFromString(attributeMap.get(VERTEX_CLASS_ATTRIBUTE).getValue());
            Class<? extends Mask<?, ?>> maskClass = (Class<? extends Mask<?, ?>>) getClassFromString(attributeMap.get(MASK_CLASS_ATTRIBUTE).getValue());
            int parameterCount = Integer.parseInt(attributeMap.get(PARAMETER_COUNT_ATTRIBUTE).getValue());
            Class<?>[] parameterTypes = new Class[parameterCount];
            String[] parameterValues = new String[parameterCount];
            for (int i = 0; i < parameterCount; ++i) {
                parameterTypes[i] = getClassFromString(attributeMap.get(PARAMETER_CLASS_PREFIX + i).getValue());
                Attribute valueAttribute = attributeMap.get(PARAMETER_VALUE_PREFIX + i);
                if (valueAttribute != null && valueAttribute.getType() != AttributeType.NULL) {
                    parameterValues[i] = valueAttribute.getValue();
                }
            }

            MaskGraphVertex<?> vertex;
            if (MaskConstructorVertex.class.equals(vertexClass)) {
                vertex = new MaskConstructorVertex(maskClass.getConstructor(parameterTypes));
            } else if (MaskMethodVertex.class.equals(vertexClass)) {
                vertex = new MaskMethodVertex(maskClass.getMethod(attributeMap.get(EXECUTABLE_ATTRIBUTE).getValue(), parameterTypes), maskClass);
            } else if (MapMaskMethodVertex.class.equals(vertexClass)) {
                vertex = new MapMaskMethodVertex(MapMaskMethods.class.getMethod(attributeMap.get(EXECUTABLE_ATTRIBUTE).getValue(), parameterTypes));
            } else {
                throw new IllegalArgumentException(String.format("Unrecognized vertex class: %s", vertexClass.getName()));
            }

            Parameter[] parameters = vertex.getExecutable().getParameters();
            for (int i = 0; i < parameterCount; ++i) {
                vertex.setParameter(parameters[i].getName(), parameterValues[i]);
            }
            vertex.setIdentifier(attributeMap.get(IDENTIFIER_NAME_ATTRIBUTE).getValue());

            return vertex;
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not parse vertex", e);
        }
    }

    private static MaskMethodEdge getMaskMethodEdgeFromAttributes(Map<String, Attribute> attributeMap) {
        if (attributeMap.isEmpty()) {
            return new MaskMethodEdge(null, null);
        }
        return new MaskMethodEdge(attributeMap.get(RESULT_NAME_ATTRIBUTE).getValue(), attributeMap.get(PARAMETER_NAME_ATTRIBUTE).getValue());
    }

    private static Map<String, Attribute> getAttributeMap(MaskMethodEdge maskMethodEdge) {
        Map<String, Attribute> attributeMap = new LinkedHashMap<>();
        attributeMap.put(PARAMETER_NAME_ATTRIBUTE, DefaultAttribute.createAttribute(maskMethodEdge.getParameterName()));
        attributeMap.put(RESULT_NAME_ATTRIBUTE, DefaultAttribute.createAttribute(maskMethodEdge.getResultName()));
        return attributeMap;
    }

    private static Map<String, Attribute> getAttributeMap(MaskGraphVertex<?> vertex) {
        Map<String, Attribute> attributeMap = new LinkedHashMap<>();
        attributeMap.put(VERTEX_CLASS_ATTRIBUTE, DefaultAttribute.createAttribute(vertex.getClass().getName()));
        attributeMap.put(MASK_CLASS_ATTRIBUTE, DefaultAttribute.createAttribute(vertex.getExecutorClass().getName()));
        attributeMap.put(IDENTIFIER_NAME_ATTRIBUTE, DefaultAttribute.createAttribute(vertex.getIdentifier()));
        if (vertex instanceof MaskMethodVertex || vertex instanceof MapMaskMethodVertex) {
            attributeMap.put(EXECUTABLE_ATTRIBUTE, DefaultAttribute.createAttribute(vertex.getExecutable().getName()));
        }
        Parameter[] parameters = vertex.getExecutable().getParameters();
        for (int i = 0; i < parameters.length; ++i) {
            Parameter parameter = parameters[i];
            if (!Mask.class.isAssignableFrom(MaskReflectUtil.getActualTypeClass(vertex.getExecutorClass(), parameter.getParameterizedType()))) {
                attributeMap.put(PARAMETER_VALUE_PREFIX + i, objectToAttribute(vertex.getParameterExpression(parameter)));
            }
            attributeMap.put(PARAMETER_CLASS_PREFIX + i, DefaultAttribute.createAttribute(parameter.getType().getName()));
        }
        attributeMap.put(PARAMETER_COUNT_ATTRIBUTE, DefaultAttribute.createAttribute(parameters.length));
        return attributeMap;
    }

    public static void exportGraph(Graph<MaskGraphVertex<?>, MaskMethodEdge> graph, File outputFile) throws IOException {
        DebugUtil.timedRun("Graph Export", () -> EXPORTER.exportGraph(graph, outputFile));
    }

    public static void importGraph(Graph<MaskGraphVertex<?>, MaskMethodEdge> graph, File inputFile) throws IOException {
        DebugUtil.timedRun("Graph Import", () -> IMPORTER.importGraph(graph, inputFile));
    }

    private static Attribute objectToAttribute(Object object) {
        if (object instanceof Long) {
            return DefaultAttribute.createAttribute((Long) object);
        } else if (object instanceof Integer) {
            return DefaultAttribute.createAttribute((Integer) object);
        } else if (object instanceof Double) {
            return DefaultAttribute.createAttribute((Double) object);
        } else if (object instanceof Float) {
            return DefaultAttribute.createAttribute((Float) object);
        } else if (object instanceof Short) {
            return DefaultAttribute.createAttribute(Integer.valueOf((Short) object));
        } else if (object instanceof Boolean) {
            return DefaultAttribute.createAttribute((Boolean) object);
        } else if (object instanceof String) {
            return DefaultAttribute.createAttribute((String) object);
        } else if (object == null) {
            return DefaultAttribute.createAttribute("");
        } else {
            return DefaultAttribute.createAttribute(object.toString());
        }
    }

    private static Class<?> getClassFromString(String className) throws ClassNotFoundException {
        return switch (className) {
            case "int" -> int.class;
            case "boolean" -> boolean.class;
            case "long" -> long.class;
            case "short" -> short.class;
            case "float" -> float.class;
            default -> Class.forName(className);
        };
    }
}