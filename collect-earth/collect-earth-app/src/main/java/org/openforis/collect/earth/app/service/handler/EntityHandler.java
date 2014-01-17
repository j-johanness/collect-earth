package org.openforis.collect.earth.app.service.handler;

import java.util.List;

import org.openforis.collect.earth.app.service.CollectParametersHandlerService;
import org.openforis.idm.metamodel.CodeAttributeDefinition;
import org.openforis.idm.metamodel.NodeDefinition;
import org.openforis.idm.model.CodeAttribute;
import org.openforis.idm.model.Entity;
import org.openforis.idm.model.EntityBuilder;
import org.openforis.idm.model.Node;

/**
 * @author Alfonso Sanchez-Paus Diaz
 *
 */
public class EntityHandler extends AbstractAttributeHandler<Entity> {

	// Expected : colllect_entity_topography[house].code_coverage=XX
	private static final String PREFIX = "entity_";

	public EntityHandler() {
		super(PREFIX);
	}

	@Override
	public void addOrUpdate(String parameterName, String parameterValue, Entity parentEntity, int childParameterIndex) {
		
		String[] parameterValues = parameterValue.split(CollectParametersHandlerService.PARAMETER_SEPARATOR);
		
		int index = 0; 
		for (String parameterVal : parameterValues) {
			
			// Expected parameter name:
			// colllect_entity_topography[house].code_coverage=XX
			parameterName = removePrefix(parameterName);
			String childEntityName = getEntityName(parameterName);
			String keyValue = getEntityKey(parameterName);
			String entityAttribute = getEntityAttribute(parameterName);
	
			Entity childEntity = geChildEntity(parentEntity, childEntityName, keyValue);
			if (childEntity == null) {
				childEntity = EntityBuilder.addEntity(parentEntity, childEntityName);
			}
			AbstractAttributeHandler cah = null;
			if (parameterName.contains("code_")) {
				cah = new CodeAttributeHandler();
			} else if (parameterName.contains("integer_")) {
				cah = new IntegerAttributeHandler();
			} else if (parameterName.contains("real_")) {
				cah = new RealAttributeHandler();
			} else if (parameterName.contains("text_")) {
				cah = new TextAttributeHandler();
			} else if (parameterName.contains("coord_")) {
				cah = new CoordinateAttributeHandler();
			} else if (parameterName.contains("date_")) {
				cah = new DateAttributeHandler();
			} else {
				throw new IllegalArgumentException("Unknown type of parameter " + parameterName);
			}
	
			cah.addOrUpdate(entityAttribute, parameterVal, childEntity, index);
			index++;
		}
		

	}

	@Override
	protected void addToEntity(String parameterName, String parameterValue, Entity entity) {
	}

	private Entity geChildEntity(Entity parentEntity, String entityName, String entityKey) {
		List<Node<? extends NodeDefinition>> entities = parentEntity.getAll(entityName);
		Entity foundEntity = null;
		if (entities != null) {
			for (Node<? extends NodeDefinition> entity : entities) {
				String key = getEntityKey((Entity) entity);
				if (key != null && key.equals(entityKey)) {
					foundEntity = (Entity) entity;
					break;
				}

			}
		}
		return foundEntity;
	}

	@Override
	public String getAttributeFromParameter(String parameterName, Entity entity, int index) {
		return "";
	}

	@Override
	protected Entity getAttributeValue(String parameterValue) {
		return EntityBuilder.createEntity(null, parameterValue);
	}

	private String getEntityAttribute(String parameterName) {
		int indexOfDot = parameterName.indexOf('.');
		return parameterName.substring(indexOfDot + 1);
	}

	public String getEntityKey(Entity entity) {
		String key = null;
		CodeAttributeDefinition enumeratingKeyCodeAttribute = entity.getDefinition().getEnumeratingKeyCodeAttribute();
		CodeAttribute keyAttribute = null;

		List<Node<? extends NodeDefinition>> children = entity.getChildren();
		for (Node<? extends NodeDefinition> child : children) {
			if (child.getName().equals(enumeratingKeyCodeAttribute.getName())) {
				keyAttribute = (CodeAttribute) child;
			}
		}

		if (keyAttribute != null) {
			key = keyAttribute.getValue().getCode();
		}

		return key;
	}

	private String getEntityKey(String parameterName) {
		int indexOfKeyStart = parameterName.indexOf("[");
		int indexOfKeyEnd = parameterName.indexOf("]");
		return parameterName.substring(indexOfKeyStart + 1, indexOfKeyEnd);
	}

	// topography[house].code_coverage=XX
	private String getEntityName(String parameterName) {
		int indexOfKey = parameterName.indexOf("[");
		return parameterName.substring(0, indexOfKey);

	}

	@Override
	public boolean isParseable(Node value) {
		return value instanceof Entity;
	}

}