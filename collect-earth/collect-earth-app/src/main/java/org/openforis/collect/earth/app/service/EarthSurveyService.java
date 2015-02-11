package org.openforis.collect.earth.app.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.openforis.collect.earth.app.EarthConstants;
import org.openforis.collect.earth.app.service.LocalPropertiesService.EarthProperty;
import org.openforis.collect.earth.app.view.Messages;
import org.openforis.collect.earth.core.handlers.BalloonInputFieldsUtils;
import org.openforis.collect.earth.core.handlers.DateAttributeHandler;
import org.openforis.collect.earth.core.model.PlacemarkInputFieldInfo;
import org.openforis.collect.earth.core.model.PlacemarkUpdateResult;
import org.openforis.collect.manager.RecordManager;
import org.openforis.collect.manager.SurveyManager;
import org.openforis.collect.manager.exception.SurveyValidationException;
import org.openforis.collect.model.CollectRecord;
import org.openforis.collect.model.CollectRecord.Step;
import org.openforis.collect.model.CollectSurvey;
import org.openforis.collect.model.RecordValidationReportGenerator;
import org.openforis.collect.model.RecordValidationReportItem;
import org.openforis.collect.persistence.RecordPersistenceException;
import org.openforis.collect.persistence.SurveyImportException;
import org.openforis.idm.metamodel.NodeDefinition;
import org.openforis.idm.metamodel.NodeLabel.Type;
import org.openforis.idm.metamodel.Schema;
import org.openforis.idm.metamodel.xml.IdmlParseException;
import org.openforis.idm.model.Entity;
import org.openforis.idm.model.Node;
import org.openforis.idm.model.TextAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EarthSurveyService {

	private static final String COLLECT_BOOLEAN_ACTIVELY_SAVED = "collect_boolean_actively_saved"; //$NON-NLS-1$
	private static final String COLLECT_DATE_ACTIVELY_SAVED_ON = "collect_date_actively_saved_on"; //$NON-NLS-1$
	private static final String COLLECT_TEXT_OPERATOR = "collect_text_operator"; //$NON-NLS-1$
	private static final String SKIP_FILLED_PLOT_PARAMETER = "jump_to_next_plot"; //$NON-NLS-1$
	
	private CollectSurvey collectSurvey;

	@Autowired
	private LocalPropertiesService localPropertiesService;


	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	@Autowired
	private RecordManager recordManager;

	@Autowired
	private SurveyManager surveyManager;
	
	private BalloonInputFieldsUtils collectParametersHandler;

	public EarthSurveyService() {
		collectParametersHandler = new BalloonInputFieldsUtils();
	}
	
	private void addLocalProperties(Map<String, String> placemarkParameters) {
		placemarkParameters.put(SKIP_FILLED_PLOT_PARAMETER, localPropertiesService.shouldJumpToNextPlot() + ""); //$NON-NLS-1$
	}

	private void addResultParameter(Map<String, String> placemarkParameters, boolean found) {
		placemarkParameters.put(EarthConstants.PLACEMARK_FOUND_PARAMETER, found + ""); //$NON-NLS-1$
	}

	private void addValidationMessages(Map<String, String> parameters, CollectRecord record) {
		// Validation
		recordManager.validate(record);

		final RecordValidationReportGenerator reportGenerator = new RecordValidationReportGenerator(record);
		final List<RecordValidationReportItem> validationItems = reportGenerator.generateValidationItems();

		for (final RecordValidationReportItem recordValidationReportItem : validationItems) {
			String label = ""; //$NON-NLS-1$
			if (recordValidationReportItem.getNodeId() != null) {
				final Node<?> node = record.getNodeByInternalId(recordValidationReportItem.getNodeId());
				label = node.getDefinition().getLabel(Type.INSTANCE, localPropertiesService.getUiLanguage().name().toLowerCase() );

				String message = recordValidationReportItem.getMessage();
				if (message.equals("Reason blank not specified")) { //$NON-NLS-1$
					message = Messages.getString("EarthSurveyService.9"); //$NON-NLS-1$
				}

				parameters.put("validation_" + node.getDefinition().getName(), label + " - " + message); //$NON-NLS-1$ //$NON-NLS-2$

			} else {
				label = recordValidationReportItem.getPath();
				parameters.put("validation_" + label, label + " - " + recordValidationReportItem.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
			}

		}

		if (validationItems.size() > 0) {
			parameters.put("valid_data", "false"); //$NON-NLS-1$ //$NON-NLS-2$
		}

	}
	
	private Map<String, String> extractValidationMessagesByParameter(CollectRecord record) {
		Map<String, String> result = new HashMap<String, String>();
		
		// Validation
		recordManager.validate(record);

		final RecordValidationReportGenerator reportGenerator = new RecordValidationReportGenerator(record);
		final List<RecordValidationReportItem> validationItems = reportGenerator.generateValidationItems();

		Map<String, String> htmlParameterNameByNodePath = collectParametersHandler.getHtmlParameterNameByNodePath(record.getRootEntity().getDefinition());

		for (final RecordValidationReportItem validationReportItem : validationItems) {
			String nodePath = validationReportItem.getPath();
			String parameterName = htmlParameterNameByNodePath.get(nodePath);
			String label = ""; //$NON-NLS-1$

			String validationReportItemMessage = validationReportItem.getMessage();
			String message;
			if (validationReportItem.getNodeId() != null) {
				final Node<?> node = record.getNodeByInternalId(validationReportItem.getNodeId());
				NodeDefinition nodeDef = node.getDefinition();
				label = nodeDef.getLabel(Type.INSTANCE, localPropertiesService.getUiLanguage().name().toLowerCase() );
				
				message = getAdaptedValidationMessage(validationReportItemMessage);
			} else {
				label = validationReportItem.getPath();
				message = label + " - " + validationReportItemMessage;
			}
			result.put(parameterName, message); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return result;
	}

	protected String getAdaptedValidationMessage(String validationMessage) {
		String message;
		if (validationMessage.equals("Reason blank not specified")) { //$NON-NLS-1$
			message = Messages.getString("EarthSurveyService.9"); //$NON-NLS-1$
		} else {
			message = validationMessage;
		}
		return message;
	}

	private CollectRecord createRecord(String sessionId) throws RecordPersistenceException {
		final Schema schema = getCollectSurvey().getSchema();
		final CollectRecord record = recordManager
				.create(getCollectSurvey(), schema.getRootEntityDefinition(EarthConstants.ROOT_ENTITY_NAME), null, null, sessionId);
		return record;
	}

	public CollectSurvey getCollectSurvey() {
		return collectSurvey;
	}

	private String getIdmFilePath() {
		return localPropertiesService.getImdFile();
	}

	public Map<String, String> getPlacemark(String placemarkId) {
		
		final List<CollectRecord> summaries = recordManager.loadSummaries(getCollectSurvey(), EarthConstants.ROOT_ENTITY_NAME, placemarkId);
		
		
		CollectRecord record = null;
		Map<String, String> placemarkParameters = null;
		if (summaries.size() > 0) {
			record = summaries.get(0);
			record = recordManager.load(getCollectSurvey(), record.getId(), Step.ENTRY);
			placemarkParameters = collectParametersHandler.getValuesByHtmlParameters(record.getRootEntity());

			if ((placemarkParameters.get("collect_code_canopy_cover") != null) && placemarkParameters.get("collect_code_canopy_cover").equals("0")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				placemarkParameters.put("collect_code_canopy_cover", //$NON-NLS-1$
						"0;collect_code_deforestation_reason=" + placemarkParameters.get("collect_code_deforestation_reason")); //$NON-NLS-1$ //$NON-NLS-2$
			}
			// For the PNG version with the old name
			if ((placemarkParameters.get("collect_code_crown_cover") != null) && placemarkParameters.get("collect_code_crown_cover").equals("0")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				placemarkParameters.put("collect_code_crown_cover", //$NON-NLS-1$
						"0;collect_code_deforestation_reason=" + placemarkParameters.get("collect_code_deforestation_reason")); //$NON-NLS-1$ //$NON-NLS-2$
			}

		
			addResultParameter(placemarkParameters, true);
					
		} else {
			placemarkParameters = new HashMap<String, String>();
			addResultParameter(placemarkParameters, false);
		}

		addLocalProperties(placemarkParameters);
		
		return placemarkParameters;
	}

	/**
	 * Return a list of the Collect records that have been updated since the time passed as an argument.
	 * This method is useful to update the status of the placemarks ( updating the icon on the KML using a NetworkLink )
	 * @param updatedSince  The date from which we want to find out if there were any records that were updates/added
	 * @return The list of record that have been updated since the time stated in updatedSince
	 */
	public List<CollectRecord> getRecordsSavedSince(Date updatedSince) {

		final List<CollectRecord> summaries = recordManager.loadSummaries(getCollectSurvey(), EarthConstants.ROOT_ENTITY_NAME, updatedSince );
		
		if ((updatedSince != null) && (summaries != null) && !summaries.isEmpty()) {
			final List<CollectRecord> records = new ArrayList<CollectRecord>();
			for (CollectRecord collectRecord : summaries) {
				final CollectRecord record = recordManager.load(getCollectSurvey(), collectRecord.getId(), Step.ENTRY);
				records.add(record);
			}
			return records;
		} else {
			return null;
		}
	}
	
	
	public String[] getPlacemarksId(List<CollectRecord> listOfRecords) {
		if (listOfRecords == null) {
			return new String[0];
		}
		final String[] placemarIds = new String[listOfRecords.size()];
		for (int i = 0; i < listOfRecords.size(); i++) {
			if (listOfRecords.get(i).getRootEntity().get("id", 0) != null) { //$NON-NLS-1$
				placemarIds[i] = ((TextAttribute) listOfRecords.get(i).getRootEntity().get("id", 0)).getValue().getValue(); //$NON-NLS-1$
			}
		}

		return placemarIds;
	}
	
	
	/**
	 * Return the list of the IDs of records that are already saved (completely or partially) in the database.
	 * @return The list of the IDs of the records for the survey in the DB
	 */
	public String[] getRecordsSavedIDs() {
		final List<CollectRecord> recordsSavedForSurvey = recordManager.loadSummaries(getCollectSurvey(), EarthConstants.ROOT_ENTITY_NAME );
		
		if ((recordsSavedForSurvey !=null) && !recordsSavedForSurvey.isEmpty()) {
			return getPlacemarksId( recordsSavedForSurvey );
		} else {
			return null;
		}
	}

	@PostConstruct
	private void init() throws FileNotFoundException, IdmlParseException, SurveyImportException {
		// Initialize the Collect survey using the idm
		// This is only done if the survey has not yet been created in the DB

		if (getCollectSurvey() == null) {
			CollectSurvey survey;
			try {
				File idmSurveyModel = new File(getIdmFilePath());
				if (idmSurveyModel.exists()) {
					survey = surveyManager.unmarshalSurvey(new FileInputStream(idmSurveyModel),true,true);
					if (surveyManager.getByUri( survey.getUri() ) == null) { // NOT IN
						// THE DB
						String surveyName = EarthConstants.EARTH_SURVEY_NAME + localPropertiesService.getValue( EarthProperty.SURVEY_NAME );
						survey = surveyManager.importModel(idmSurveyModel, surveyName, false );
					} else { // UPDATE ALREADY EXISTANT MODEL
						survey = surveyManager.updateModel(idmSurveyModel, false );
					}
					setCollectSurvey(survey);
				} else {
					logger.error("The survey definition file could not be found in " + idmSurveyModel.getAbsolutePath()); //$NON-NLS-1$
				}
			} catch (final SurveyValidationException e) {
				logger.error("Unable to validate survey at " + getIdmFilePath(), e); //$NON-NLS-1$
				e.printStackTrace();
			}
		}

	}

	public boolean isPlacemarEdited(Map<String, String> parameters) {
		return (parameters != null) && (parameters.get(COLLECT_BOOLEAN_ACTIVELY_SAVED) != null)
				&& parameters.get(COLLECT_BOOLEAN_ACTIVELY_SAVED).equals("false"); //$NON-NLS-1$
	}

	public boolean isPlacemarkSavedActively(Map<String, String> parameters) {
		return (parameters != null) && (parameters.get(COLLECT_BOOLEAN_ACTIVELY_SAVED) != null)
				&& parameters.get(COLLECT_BOOLEAN_ACTIVELY_SAVED).equals("true"); //$NON-NLS-1$
	}

	private void setCollectSurvey(CollectSurvey collectSurvey) {
		this.collectSurvey = collectSurvey;
	}

	private void setPlacemarkSavedOn(Map<String, String> parameters) {
		parameters.put(COLLECT_DATE_ACTIVELY_SAVED_ON, DateAttributeHandler.DATE_ATTRIBUTE_FORMAT.format(new Date()));
	}

	private void setPlacemarkSavedActively(Map<String, String> parameters, boolean value) {
		parameters.put(COLLECT_BOOLEAN_ACTIVELY_SAVED, value + ""); //$NON-NLS-1$

	}

	public synchronized boolean storePlacemarkOld(Map<String, String> parameters, String sessionId) {

		final List<CollectRecord> summaries = recordManager.loadSummaries(getCollectSurvey(), EarthConstants.ROOT_ENTITY_NAME, parameters.get("collect_text_id")); //$NON-NLS-1$
		boolean success = false;

		try {
			// Add the operator to the collected data
			parameters.put(COLLECT_TEXT_OPERATOR, localPropertiesService.getOperator());

			CollectRecord record = null;
			Entity plotEntity = null;

			if (summaries.size() > 0) { // DELETE IF ALREADY PRESENT
				record = summaries.get(0);
				recordManager.delete(record.getId());
				record = createRecord(sessionId);
				plotEntity = record.getRootEntity();
			} else {
				// Create new record
				record = createRecord(sessionId);
				plotEntity = record.getRootEntity();
				logger.warn("Creating a new plot entity with data " + parameters.toString()); //$NON-NLS-1$
			}

			boolean userClickOnSaveAndValidate = isPlacemarkSavedActively(parameters);
			if (userClickOnSaveAndValidate) {
				setPlacemarkSavedOn(parameters);
			}

			// Populate the data of the record using the HTTP parameters received
			collectParametersHandler.saveToEntity(parameters, plotEntity);

			// Do not validate unless actively saved
			if (userClickOnSaveAndValidate) {
				addValidationMessages(parameters, record);
			}

			// Do not save unless there is no validation errors
			if (((record.getErrors() == 0) && (record.getSkipped() == 0) && userClickOnSaveAndValidate) || !userClickOnSaveAndValidate) {
				record.setModifiedDate(new Date());
				recordManager.save(record, sessionId);
			} else {
				setPlacemarkSavedActively(parameters, false);
			}
			success = true;
		} catch (final RecordPersistenceException e) {
			logger.error("Error while storing the record " + e.getMessage(), e); //$NON-NLS-1$
		}
		return success;
	}

	public synchronized PlacemarkUpdateResult storePlacemark(Map<String, String> parameters, String sessionId) {
		PlacemarkUpdateResult result = new PlacemarkUpdateResult();
		
		try {
			// Add the operator to the collected data
			parameters.put(COLLECT_TEXT_OPERATOR, localPropertiesService.getOperator());

			CollectRecord record = loadOrCreateRecord(parameters, sessionId);
			Entity plotEntity = record.getRootEntity();

			if (isPlacemarkSavedActively(parameters)) {
				setPlacemarkSavedOn(parameters);
			}

			// Populate the data of the record using the HTTP parameters received
			collectParametersHandler.saveToEntity(parameters, plotEntity);

			boolean userClickOnSaveAndValidate = isPlacemarkSavedActively(parameters);

			if (((record.getErrors() == 0) && (record.getSkipped() == 0) && userClickOnSaveAndValidate) || !userClickOnSaveAndValidate) {
				record.setModifiedDate(new Date());
				result.setSuccess(true);
				recordManager.save(record, sessionId);
			} else {
				setPlacemarkSavedActively(parameters, false);
			}
			Set<String> parameterNames = parameters.keySet();
			Map<String, PlacemarkInputFieldInfo> infoByParameterName = collectParametersHandler.extractFieldInfoByParameterName(parameterNames, record);
			result.setInputFieldInfoByParameterName(infoByParameterName);
		} catch (final RecordPersistenceException e) {
			logger.error("Error while storing the record " + e.getMessage(), e); //$NON-NLS-1$
			result.setSuccess(false);
		}
		return result;
	}
	
	protected CollectRecord loadOrCreateRecord(Map<String, String> parameters,
			String sessionId) throws RecordPersistenceException {
		CollectRecord record;
		List<CollectRecord> summaries = recordManager.loadSummaries(getCollectSurvey(), EarthConstants.ROOT_ENTITY_NAME, parameters.get("collect_text_id")); //$NON-NLS-1$
		if (! summaries.isEmpty()) {
			record = recordManager.load(getCollectSurvey(), summaries.get(0).getId());
		} else {
			// Create new record
			record = createRecord(sessionId);
			logger.warn("Creating a new plot entity with data " + parameters.toString()); //$NON-NLS-1$
		}
		return record;
	}

	private Map<String, String> calculateChanges(
			Map<String, String> oldPlacemarkParameters,
			Map<String, String> parameters) {
		Map<String, String> changedParameters = new HashMap<>(parameters.size());
		for (Entry<String, String> entry : parameters.entrySet()) {
			String param = entry.getKey();
			String newValue = entry.getValue();
			String oldValue = oldPlacemarkParameters.get(param);
			if (oldValue == null || ! oldValue.equals(newValue) ) {
				changedParameters.put(param, newValue);
			}
		}
		return changedParameters;
	}
}
