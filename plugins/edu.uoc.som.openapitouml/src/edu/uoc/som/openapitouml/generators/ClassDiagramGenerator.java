package edu.uoc.som.openapitouml.generators;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.uml2.uml.AggregationKind;
import org.eclipse.uml2.uml.Association;
import org.eclipse.uml2.uml.Class;
import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.Constraint;
import org.eclipse.uml2.uml.Enumeration;
import org.eclipse.uml2.uml.EnumerationLiteral;
import org.eclipse.uml2.uml.Generalization;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.NamedElement;
import org.eclipse.uml2.uml.Namespace;
import org.eclipse.uml2.uml.OpaqueExpression;
import org.eclipse.uml2.uml.Package;
import org.eclipse.uml2.uml.PackageableElement;
import org.eclipse.uml2.uml.ParameterDirectionKind;
import org.eclipse.uml2.uml.PrimitiveType;
import org.eclipse.uml2.uml.Property;
import org.eclipse.uml2.uml.Type;
import org.eclipse.uml2.uml.UMLFactory;
import org.eclipse.uml2.uml.UMLPackage;
import org.eclipse.uml2.uml.resource.UMLResource;

import edu.uoc.som.openapi.JSONDataType;
import edu.uoc.som.openapi.Operation;
import edu.uoc.som.openapi.Parameter;
import edu.uoc.som.openapi.ParameterLocation;
import edu.uoc.som.openapi.Path;
import edu.uoc.som.openapi.Root;
import edu.uoc.som.openapi.Schema;
import edu.uoc.som.openapitouml.utils.OpenAPIUtils;

public class ClassDiagramGenerator implements Serializable {
	/**
		 * 
		 */
	private static final long serialVersionUID = 1L;
	private UMLFactory umlFactory;
	private ResourceSet resourceSet;

	public ClassDiagramGenerator() {

		umlFactory = UMLFactory.eINSTANCE;
		resourceSet = new ResourceSetImpl();
		resourceSet.getPackageRegistry().put(UMLPackage.eNS_URI, UMLPackage.eINSTANCE);
		resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put(UMLResource.FILE_EXTENSION,
				UMLResource.Factory.INSTANCE);

	}

	public Model generateClassDiagramFromOpenAPI(Root root, String modelName) {
		Model model = umlFactory.createModel();
		model.setName(modelName);
		Package package_ = umlFactory.createPackage();
		package_.setName(modelName);
		model.getPackagedElements().add(package_);
		Package types = umlFactory.createPackage();
		types.setName("types");
		model.getPackagedElements().add(types);

		Map<Schema, Class> map = new HashMap<Schema, Class>();

		// generate classes
		for (Schema schema : root.getApi().getDefinitions()) {
			if (isObject(schema)) {
				Class clazz = umlFactory.createClass();
				clazz.setName(schema.getName());

				package_.getOwnedTypes().add(clazz);
				map.put(schema, clazz);
				addProperties(types, schema, clazz);
				if(!schema.getAllOf().isEmpty()) {
					for(Schema allOfItem : schema.getAllOf()) {
						if(allOfItem.getDeclaringContext()!= null && allOfItem.getDeclaringContext().equals(schema)) {
							addProperties(types, allOfItem, clazz);
						}
					}
				}
			}
		}
		// resolve superclasses
		for (Schema schema : root.getApi().getDefinitions()) {
			if (isObject(schema)) {
						if(!schema.getAllOf().isEmpty()) {
							Class child = map.get(schema);
							if(child != null)
							for(Schema allOfItem : schema.getAllOf()) {
							Class parent =	map.get(allOfItem);
							
							if(parent != null) {
								Generalization generation = umlFactory.createGeneralization();
								generation.setGeneral(parent);
								child.getGeneralizations().add(generation);
							}
							}
						}
					}
				}
		// resolve associations
		for (Schema schema : root.getApi().getDefinitions()) {
			if (isObject(schema)) {
				for (Schema property : schema.getProperties()) {
					if (isObject(property) || isArrayOfObjects(property)) {
						Association association = umlFactory.createAssociation();
						association.setName(schema.getName() + "_" + property.getName());
						Property firstOwnedEnd = umlFactory.createProperty();
						association.getOwnedEnds().add(firstOwnedEnd);
						Property secondOwnedEnd = umlFactory.createProperty();
						association.getOwnedEnds().add(secondOwnedEnd);
						firstOwnedEnd.setName(schema.getName());
						firstOwnedEnd.setType(map.get(schema));
						secondOwnedEnd.setName(property.getName());
						secondOwnedEnd.setAggregation(AggregationKind.COMPOSITE_LITERAL);
						if (schema.getRequired().contains(property))
							secondOwnedEnd.setLower(1);
						else
							secondOwnedEnd.setLower(0);
						if (!property.getType().equals(JSONDataType.ARRAY)) {
							Class type = map.get(property.getValue());
							secondOwnedEnd.setType(type);

						} else {
							secondOwnedEnd.setUpper(-1);
							secondOwnedEnd.setType(map.get(property.getItems()));

						}
						association.getNavigableOwnedEnds().add(secondOwnedEnd);
						package_.getPackagedElements().add(association);
					}

				}
			}
		}
		// resolve associations
		for (Schema schema : root.getApi().getDefinitions()) {
			if (isObject(schema)) {
				if(!schema.getAllOf().isEmpty()) {
					
				}
			}
		}
		// add operations
		for (Operation operation : root.getApi().getAllOperations()) {
			Schema schema = OpenAPIUtils.getAppropriateLocation(root.getApi(), operation);
			Class clazz = null;
			if (schema != null) {
				if (map.get(schema) != null) {
					clazz = map.get(schema);
				}

			}
			if (clazz == null) {
				Path path = (Path) operation.eContainer();
				String resource = OpenAPIUtils.getLastMeaningfullSegment(path.getRelativePath());
				NamedElement namedElement = package_.getOwnedMember(StringUtils.capitalize(resource), false,
						UMLPackage.eINSTANCE.getClass_());
				if (namedElement != null) {
					clazz = (Class) namedElement;
				} else {
					clazz = umlFactory.createClass();
					clazz.setName(StringUtils.capitalize(resource));
					package_.getOwnedTypes().add(clazz);
				}
			}

			org.eclipse.uml2.uml.Operation umlOperation = umlFactory.createOperation();
			umlOperation.setName(OpenAPIUtils.getOperationName(operation));
			clazz.getOwnedOperations().add(umlOperation);
			for (Parameter parameter : operation.getParameters()) {
				org.eclipse.uml2.uml.Parameter umlParameter = umlFactory.createParameter();
				umlParameter.setName(parameter.getName());
				umlParameter.setDirection(ParameterDirectionKind.IN_LITERAL);
				if (parameter.getDefault() != null)
					umlParameter.setDefault(parameter.getDefault());
				if (parameter.getMultipleOf() != null)
					addConstraint(umlOperation, parameter.getName(), "multipleOfConstraint",
							"self." + parameter.getName() + ".div(" + parameter.getMultipleOf() + ") = 0");
				if (parameter.getMaximum() != null) {
					if (parameter.getExclusiveMaximum() != null && parameter.getExclusiveMaximum().equals(Boolean.TRUE))
						addConstraint(umlOperation, umlParameter.getName(), "maximumConstraint",
								"self." + umlParameter.getName() + " < " + parameter.getMaximum());
					else
						addConstraint(umlOperation, umlParameter.getName(), "maximumConstraint",
								"self." + umlParameter.getName() + " <= " + parameter.getMaximum());
				}
				if (parameter.getMinimum() != null) {
					if (parameter.getExclusiveMinimum() != null && parameter.getExclusiveMinimum().equals(Boolean.TRUE))
						addConstraint(umlOperation, umlParameter.getName(), "minimumConstraint",
								"self." + umlParameter.getName() + " > " + parameter.getMinimum());
					else
						addConstraint(umlOperation, umlParameter.getName(), "minimumConstraint",
								"self." + umlParameter.getName() + " >= " + parameter.getMinimum());
				}
				if (parameter.getMaxLength() != null)
					addConstraint(umlOperation, parameter.getName(), "maxLengthConstraint",
							"self." + parameter.getName() + ".size() <= " + parameter.getMaxLength());
				if (parameter.getMinLength() != null)
					addConstraint(umlOperation, parameter.getName(), "minLengthConstraint",
							"self." + parameter.getName() + ".size() >= " + parameter.getMinLength());
				if (parameter.getLocation().equals(ParameterLocation.BODY)) {
					if (parameter.getSchema() != null) {
						if (parameter.getSchema().getType().equals(JSONDataType.ARRAY)) {
							umlParameter.setType(map.get(parameter.getSchema().getItems()));
							if (parameter.getSchema().getMaxItems() != null) {
								umlParameter.setUpper(parameter.getSchema().getMaxItems());
							} else
								umlParameter.setUpper(-1);
							if (parameter.getSchema().getMinItems() != null)
								umlParameter.setLower(parameter.getSchema().getMinItems());
							else
								umlParameter.setLower(0);
						} else {
							umlParameter.setType(map.get(parameter.getSchema()));
						}
					}
				} else {
					if (parameter.getRequired() != null && parameter.getRequired().equals(Boolean.TRUE))
						umlParameter.setLower(1);
					else
						umlParameter.setLower(0);
					if (parameter.getType().equals(JSONDataType.ARRAY)) {
						if (parameter.getMaxItems() != null)
							umlParameter.setUpper(parameter.getMaxItems());
						else
							umlParameter.setUpper(-1);
						if (!parameter.getItems().getEnum().isEmpty())
							umlParameter.setType(getOrCreateEnumeration(parameter.getItems().getEnum(),
									clazz.getName() + StringUtils.capitalize(parameter.getName()), types));
						else
							umlParameter.setType(getUMLType(types, parameter.getItems().getType(),
									parameter.getItems().getFormat()));
					} else if (!parameter.getEnum().isEmpty())
						umlParameter.setType(getOrCreateEnumeration(parameter.getEnum(),
								clazz.getName() + StringUtils.capitalize(parameter.getName()), types));
					else
						umlParameter.setType(getUMLType(types, parameter.getType(), parameter.getFormat()));
					if (parameter.getDefault() != null) {
						umlParameter.setDefault(parameter.getDefault());
					}
				}

				umlOperation.getOwnedParameters().add(umlParameter);

			}
			Schema s = operation.getProducedSchema();
			if (s != null) {
				org.eclipse.uml2.uml.Parameter returnedParameter = umlFactory.createParameter();
				Class producedClass = map.get(s);
				if (producedClass != null)
					returnedParameter.setType(producedClass);
				if (operation.IsProducingList()) {
					returnedParameter.setUpper(-1);
					returnedParameter.setLower(0);
				}
				umlOperation.getOwnedParameters().add(returnedParameter);
				returnedParameter.setDirection(ParameterDirectionKind.RETURN_LITERAL);

			}

		}

		return model;

	}

	private void addProperties(Package types, Schema schema, Class clazz) {
		for (Schema property : schema.getProperties()) {
			if (isPrimitive(property)) {
				Property umlProperty = umlFactory.createProperty();
				umlProperty.setName(property.getName());
				if (property.getMultipleOf() != null)
					addConstraint(clazz, umlProperty.getName(), "multipleOfConstraint",
							"self." + umlProperty.getName() + ".div(" + property.getMultipleOf() + ") = 0");
				if (property.getMaximum() != null) {
					if (property.getExclusiveMaximum() != null && property.getExclusiveMaximum().equals(Boolean.TRUE))
						addConstraint(clazz, umlProperty.getName(), "maximumConstraint",
								"self." + umlProperty.getName() + " < " + property.getMaximum());
					else
						addConstraint(clazz, umlProperty.getName(), "maximumConstraint",
								"self." + umlProperty.getName() + " <= " + property.getMaximum());
				}
				if (property.getMinimum() != null) {
					if (property.getExclusiveMinimum() != null && property.getExclusiveMinimum().equals(Boolean.TRUE))
						addConstraint(clazz, umlProperty.getName(), "minimumConstraint",
								"self." + umlProperty.getName() + " > " + property.getMinimum());
					else
						addConstraint(clazz, umlProperty.getName(), "minimumConstraint",
								"self." + umlProperty.getName() + " >= " + property.getMinimum());
				}
				if (property.getMaxLength() != null)
					addConstraint(clazz, property.getName(), "maxLengthConstraint",
							"self." + property.getName() + ".size() <= " + property.getMaxLength());
				if (property.getMinLength() != null)
					addConstraint(clazz, property.getName(), "minLengthConstraint",
							"self." + property.getName() + ".size() >= " + property.getMinLength());

				if (property.getDefault() != null)
					umlProperty.setDefault(property.getDefault());

				if (!property.getType().equals(JSONDataType.ARRAY)) {
					if (!property.getEnum().isEmpty())
						umlProperty.setType(getOrCreateEnumeration(property.getEnum(),
								schema.getName() + StringUtils.capitalize(property.getName()), types));
					else
						umlProperty.setType(getUMLType(types, property.getType(), property.getFormat()));
					if (schema.getRequired().contains(property))
						umlProperty.setLower(1);
					else
						umlProperty.setLower(0);
				} else {

					umlProperty.setUpper(-1);
					if (property.getMinItems() != null)
						umlProperty.setLower(property.getMinItems());
					else
						umlProperty.setLower(0);
					if (!property.getItems().getEnum().isEmpty())
						umlProperty.setType(getOrCreateEnumeration(property.getItems().getEnum(),
								schema.getName() + StringUtils.capitalize(property.getName()), types));
					else
						umlProperty.setType(
								getUMLType(types, property.getItems().getType(), property.getItems().getFormat()));

				}
				clazz.getOwnedAttributes().add(umlProperty);
			}
		}
	}

	private boolean isPrimitive(Schema property) {
		if (property.getType().equals(JSONDataType.BOOLEAN) || property.getType().equals(JSONDataType.INTEGER)
				|| property.getType().equals(JSONDataType.NUMBER) || property.getType().equals(JSONDataType.STRING))
			return true;
		if (property.getType().equals(JSONDataType.ARRAY) && (property.getItems().getType().equals(JSONDataType.BOOLEAN)
				|| property.getItems().getType().equals(JSONDataType.INTEGER)
				|| property.getItems().getType().equals(JSONDataType.NUMBER)
				|| property.getItems().getType().equals(JSONDataType.STRING)))
			return true;
		return false;
	}

	private boolean isObject(Schema schema) {
		if (schema.getType().equals(JSONDataType.OBJECT))
			return true;

		if (schema.getValue() != null && schema.getValue().getType().equals(JSONDataType.OBJECT))
			return true;

		if (!schema.getProperties().isEmpty())
			return true;

		if (!schema.getAllOf().isEmpty())
			return true;

		if (schema.getValue() != null && !schema.getValue().getProperties().isEmpty())
			return true;

		if (schema.getValue() != null && !schema.getValue().getAllOf().isEmpty())
			return true;
		return false;
	}

	private boolean isArrayOfObjects(Schema schema) {

		if (schema.getType().equals(JSONDataType.ARRAY) && (schema.getItems().getType().equals(JSONDataType.OBJECT)))
			return true;
		if (schema.getValue() != null && schema.getValue().getType().equals(JSONDataType.ARRAY)
				&& schema.getValue().getItems().getType().equals(JSONDataType.OBJECT))
			return true;

		if (schema.getType().equals(JSONDataType.ARRAY) && !schema.getItems().getProperties().isEmpty())
			return true;
		if (schema.getType().equals(JSONDataType.ARRAY) && !schema.getItems().getAllOf().isEmpty())
			return true;
		if (schema.getValue() != null && schema.getValue().getType().equals(JSONDataType.ARRAY)
				&& !schema.getValue().getItems().getProperties().isEmpty())
			return true;
		if (schema.getValue() != null && schema.getValue().getType().equals(JSONDataType.ARRAY)
				&& !schema.getValue().getItems().getAllOf().isEmpty())
			return true;
		return false;
	}

	private PrimitiveType getUMLType(Package types, JSONDataType jsonDataType, String format) {
		PrimitiveType type = null;
		switch (jsonDataType) {

		case INTEGER:
			if (format == null)
				type = getOrCreatePrimitiveTypeByCommonName("Integer", types);
			else if (format.equals("int32"))
				type = getOrCreatePrimitiveTypeByCommonName("Integer", types);
			else if (format.equals("int64"))
				type = getOrCreatePrimitiveTypeByCommonName("Long", types);
			else
				type = getOrCreatePrimitiveTypeByCommonName(StringUtils.capitalize(format), types);
			break;
		case NUMBER:
			if (format == null)
				type = getOrCreatePrimitiveTypeByCommonName("Number", types);
			else if (format.equals("float"))
				type = getOrCreatePrimitiveTypeByCommonName("Float", types);
			else if (format.equals("double"))
				type = getOrCreatePrimitiveTypeByCommonName("Double", types);
			else
				type = getOrCreatePrimitiveTypeByCommonName(StringUtils.capitalize(format), types);
			break;
		case STRING:
			if (format == null)
				type = getOrCreatePrimitiveTypeByCommonName("String", types);
			else if (format.equals("byte"))
				type = getOrCreatePrimitiveTypeByCommonName("Byte", types);
			else if (format.equals("binary"))
				type = getOrCreatePrimitiveTypeByCommonName("Binary", types);
			else if (format.equals("date"))
				type = getOrCreatePrimitiveTypeByCommonName("Date", types);
			else if (format.equals("date-time"))
				type = getOrCreatePrimitiveTypeByCommonName("DateTime", types);
			else if (format.equals("password"))
				type = getOrCreatePrimitiveTypeByCommonName("Password", types);
			else
				type = getOrCreatePrimitiveTypeByCommonName(StringUtils.capitalize(format), types);

			break;
		case BOOLEAN:
			type = getOrCreatePrimitiveTypeByCommonName("Boolean", types);
			break;
		case FILE:
			type = getOrCreatePrimitiveTypeByCommonName("File", types);
		default:
			break;
		}
		return type;

	}

	public UMLFactory getUmlFactory() {
		return umlFactory;
	}

	public void setUmlFactory(UMLFactory umlFactory) {
		this.umlFactory = umlFactory;
	}

	public void saveClassDiagram(Model model, URI resourceURI) throws IOException {
		Resource resource = resourceSet.createResource(resourceURI);
		resource.getContents().add(model);
		resource.save(Collections.EMPTY_MAP);

	}

	private PrimitiveType getOrCreatePrimitiveTypeByCommonName(String commonName, Package types) {

		Type type = types.getOwnedType(commonName, false, UMLPackage.eINSTANCE.getPrimitiveType(), false);
		if (type != null)
			return (PrimitiveType) type;
		else {
			PrimitiveType primitiveType = umlFactory.createPrimitiveType();
			primitiveType.setName(commonName);
			types.getOwnedTypes().add(primitiveType);
			return primitiveType;
		}
	}

	private Enumeration getOrCreateEnumeration(List<String> literals, String name, Package types) {

		Type type = types.getOwnedType(name, false, UMLPackage.eINSTANCE.getEnumeration(), false);
		if (type != null)
			return (Enumeration) type;
		else {
			Enumeration enumeration = umlFactory.createEnumeration();
			enumeration.setName(name);
			types.getOwnedTypes().add(enumeration);
			for (String l : literals) {
				EnumerationLiteral literal = umlFactory.createEnumerationLiteral();
				literal.setName(l);
				enumeration.getOwnedLiterals().add(literal);
			}
			return enumeration;
		}
	}

	/**
	 * Adds a OCL constraint to a concept
	 * 
	 * @param concept
	 *            The concept which holds the constraint
	 * @param constraintName
	 *            The name of the constraint (will be eventually formed as
	 *            conceptName-constraintName-constraintType
	 * @param constraintType
	 *            The type of the constraint being applied (e.g.,
	 *            macLengthConstraint)
	 * @param constraintExp
	 *            The OCL expression
	 */
	private void addConstraint(Namespace namespace, String constraintName, String constraintType,
			String constraintExp) {
		Constraint constraint = UMLFactory.eINSTANCE.createConstraint();
		String constraintId = namespace.getName() + "-" + constraintName + "-" + constraintType;
		constraint.setName(constraintId);
		OpaqueExpression expression = UMLFactory.eINSTANCE.createOpaqueExpression();
		expression.getLanguages().add("OCL");
		expression.getBodies().add(constraintExp);
		constraint.setSpecification(expression);
		namespace.getOwnedRules().add(constraint);
	}

}
