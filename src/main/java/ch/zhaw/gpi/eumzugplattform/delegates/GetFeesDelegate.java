package ch.zhaw.gpi.eumzugplattform.delegates;

import ch.zhaw.gpi.eumzugplattform.entities.MunicipalityEntity;
import ch.zhaw.gpi.eumzugplattform.repositories.MunicipalityRepository;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Named;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.value.ObjectValue;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 
 * Serialisierte Prozessvariable mit relevanten Gebühren basierend auf Angaben
 * in der Umzugsplattform-Datenbank werden erstellt
 *
 * Zunächst wird geprüft, ob es sich um Wegzug/Zuzug oder Umzug handelt. Je
 * nachdem werden dann über ein JPA-Repository eine oder zwei Gemeinde-Objekte
 * aus der Datenbank geladen.
 *
 * Für jedes Objekt wird die relevante Gebühr (Zuzug, Wegzug oder Umzug)
 * ausgelesen und einer HashMap hinzugefügt inklusive Angabe, um welche Art von
 * Gebühr es sich handelt (z.B. "Wegzugsgebühr"). Falls mindestens eine Gebühr
 * anfällt, wird die HashMap mittels Camunda Spin in ein JSON-Objekt
 * serialisiert und der Prozessvariable feeMapSerialized zugewiesen, ansonsten
 * wird dieser Variable der Wert null zugewiesen.
 * 
 */

@Named("getFeesAdapter")
public class GetFeesDelegate implements JavaDelegate {
    // Konstanten
    public final static String MOVE_FEE_NAME = "Umzugsgebühr";
    public final static String MOVE_OUT_FEE_NAME = "Wegzugsgebühr";
    public final static String MOVE_IN_FEE_NAME = "Zuzugsgebühr";
    
    // automatische Instanz von municipalityRepository
    @Autowired
    private MunicipalityRepository municipalityRepository;
    
    // Implementiert die vom Interface "JavaDelegate" geforderte Methode.
    @Override
    public void execute(DelegateExecution execution) throws Exception {
        // ruft Methode auf um die Gebühren Map zu erhalten bekommen
        Map<String, Integer> feeMap = this.getFees(execution);

        // feeMapSerialized wird initialisiert
        ObjectValue feeMapSerialized = null;
        
        // Wenn die Gebühren Map Daten enthält hat...
        if (feeMap.size() > 0) {
            // ... wird sie serialisiert 
            feeMapSerialized = Variables
                    .objectValue(feeMap)
                    .serializationDataFormat(Variables.SerializationDataFormats.JSON)
                    .create();
        }
        
        // Die lokale Variable wird der Prozessvariable zugewiesen
        execution.setVariable("feeMapSerialized", feeMapSerialized);
    }

    private Map<String, Integer> getFees(DelegateExecution execution) {
        // Initialisiere Gebühren HashMap (feeType / feeMove)
        HashMap<String, Integer> feeMap = new HashMap<>();
        
        // liest moveIn und moveOut Variablen aus
        Integer municipalityMoveOutId = (Integer) execution.getVariable("municipalityIdMoveOut");
        Integer municipalityMoveInId = (Integer) execution.getVariable("municipalityIdMoveIn");
        
        // wenn moveOut und moveIn dasselbe
        if (municipalityMoveOutId.equals(municipalityMoveInId)) {
            // dann verarbeite Umzug
            processMove(feeMap, municipalityMoveInId);
        } else {
            // ansonsten verarbeite einen Wegzug/Zuzug
            processAdmittingOrLeave(feeMap, municipalityMoveOutId, municipalityMoveInId);
        }

        return feeMap;
    }

    private void processMove(HashMap<String, Integer> feeMap, Integer municipalityIdMoveIn) {
        // Umzugsgemeinde aus der Datenbank laden und die Gebühr in eine Variable speichern
        MunicipalityEntity municipalityMove = this.municipalityRepository.getOne(municipalityIdMoveIn);
        Integer feeMove = municipalityMove.getFeeMove();
        
        // Wenn Gebühr grösser als 0 (wenn nicht Kostenlos)
        if (feeMove > 0) {
            // Dann speichere die Art der Gebühr (feeType) und die dazugehörige Gebüren (feeMove) in der Gebühren Map
            feeMap.put(MOVE_FEE_NAME + " " + municipalityMove.getMunicipalityName(), feeMove);
        }
    }

    private void processAdmittingOrLeave(HashMap<String, Integer> feeMap, Integer municipalityIdMoveOut, Integer municipalityIdMoveIn) {
        // list Gemeindedaten für den Wegzug/Zuzug aus und speichert die Kosten in Variablen
        MunicipalityEntity municipalityMoveOut = this.municipalityRepository.getOne(municipalityIdMoveOut);
        MunicipalityEntity municipalityMoveIn = this.municipalityRepository.getOne(municipalityIdMoveIn);
        Integer feeMoveIn = municipalityMoveIn.getFeeMoveIn();
        Integer feeMoveOut = municipalityMoveOut.getFeeMoveOut();

        // wenn Wegzugsgebühr grösser als 0
        if (feeMoveOut > 0) {
            // Dann speichere die Art der Gebühr (feeType) und die dazugehörige Gebüren (feeMove) in der Gebühren Map
            feeMap.put(MOVE_OUT_FEE_NAME + " " + municipalityMoveOut.getMunicipalityName(), feeMoveOut);
        }

        // wenn Zuzugsgebühr grösser als 0
        if (feeMoveIn > 0) {
            // dann speichere die Art der Gebühr (feeType) und die dazugehörige Gebüren (feeMove) in der Gebühren Map
            feeMap.put(MOVE_IN_FEE_NAME + " " + municipalityMoveIn.getMunicipalityName(), feeMoveIn);
        }
    }
}