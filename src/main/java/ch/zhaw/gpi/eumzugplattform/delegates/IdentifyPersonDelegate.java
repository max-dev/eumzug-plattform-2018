package ch.zhaw.gpi.eumzugplattform.delegates;

import ch.ech.xmlns.ech_0007._5.SwissMunicipalityType;
import ch.ech.xmlns.ech_0044._4.DatePartiallyKnownType;
import ch.ech.xmlns.ech_0044._4.NamedPersonIdType;
import ch.ech.xmlns.ech_0044._4.PersonIdentificationType;
import ch.ech.xmlns.ech_0194._1.PersonMoveRequest;
import ch.ech.xmlns.ech_0194._1.PersonMoveResponse;
import static ch.zhaw.gpi.eumzugplattform.helpers.DateConversionHelper.DateToXMLGregorianCalendar;
import ch.zhaw.gpi.eumzugplattform.webserviceclients.ResidentRegisterWebServiceClient;
import java.math.BigInteger;
import java.util.Date;
import java.util.List;
import javax.inject.Named;
import javax.xml.datatype.XMLGregorianCalendar;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.value.ObjectValue;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * JavaDelegate für Personidentifikation im kantonalen Personenregister
 * 
 * Die Operation identifyPerson des Personenregister-Web Service (http://localhost:8083/soap/PersonenRegisterService) wird synchron aufgerufen. Der eigentliche Web Service-Client (ResidentRegisterWebServiceClient) für die Kommunikation mit diesem Service ist in einer eigenen Klasse enthalten.
 * 
 * Die Aufgabe dieser Klasse ist zunächst, die folgenden Prozessvariablen den passenden Eigenschaften der personMoveRequest-Klasse, respektive einer ihrer referenzierten Klassen zuzuweisen: businessCaseId, municipalityNameMoveOut, municipalityIdMoveOut, firstName, officialName, sex, dateOfBirth, vn (optional), localPersonId und eine neue Variable personIdCategory mit dem Wert LOC.UMZUGPLATTFORM.
 * 
 * Hinweis: Die personMoveRequest und weitere eCH-Klassen werden gebildet über ein Build-Plugin, das im pom.xml konfiguriert ist.
 * 
 * Mit der personMoveRequest-Klasse kann nun der ResidentRegisterWebServiceClient die Methode identifyPerson ausführen, welche als Antwort ein Objekt der Klasse PersonMoveResponse zurückgibt.
 * 
 * Womit wir bei der zweiten Aufgabe dieser Klasse sind, nämlich dem Interpretieren der personMoveRespone: Es werden die zwei in personMoveResponse enthaltenen Eigenschaften personKnown und sofern vorhanden moveAllowed den gleich benannten Prozessvariablen übergeben.
 * 
 * @author scep
 */
@Named("IdentifyPersonAdapter")
public class IdentifyPersonDelegate implements JavaDelegate {

    @Autowired
    private ResidentRegisterWebServiceClient personenRegisterClient;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String businessCaseId = (String) execution.getVariable("businessCaseId");

        String municipalityNameMoveOut = (String) execution.getVariable("municipalityNameMoveOut");
        Integer municipalityIdMoveOut = (Integer) execution.getVariable("municipalityIdMoveOut");

        String firstName = (String) execution.getVariable("firstName");
        String officialName = (String) execution.getVariable("officialName");
        String sex = (String) execution.getVariable("sex");
        Date dateOfBirth = (Date) execution.getVariable("dateOfBirth");
        Long vn = (Long) execution.getVariable("vn");
        String localPersonId = (String) execution.getVariable("localPersonId");

        SwissMunicipalityType swissMunicipality = new SwissMunicipalityType();
        swissMunicipality.setMunicipalityId(municipalityIdMoveOut);
        swissMunicipality.setMunicipalityName(municipalityNameMoveOut);

        NamedPersonIdType namedPersonId = new NamedPersonIdType();
        namedPersonId.setPersonIdCategory("LOC.UMZUGPLATTFORM");
        namedPersonId.setPersonId(localPersonId);

        XMLGregorianCalendar xMLGregorianCalendar = DateToXMLGregorianCalendar(dateOfBirth);

        DatePartiallyKnownType datePartiallyKnown = new DatePartiallyKnownType();
        datePartiallyKnown.setYearMonthDay(xMLGregorianCalendar);

        PersonIdentificationType personIdentification = new PersonIdentificationType();
        personIdentification.setDateOfBirth(datePartiallyKnown);
        personIdentification.setFirstName(firstName);
        personIdentification.setOfficialName(officialName);
        personIdentification.setSex(sex);
        if (vn != null) {
            personIdentification.setVn(BigInteger.valueOf(vn));
        }
        personIdentification.setLocalPersonId(namedPersonId);

        PersonMoveRequest personMoveRequest = new PersonMoveRequest();
        personMoveRequest.setMunicipality(swissMunicipality);
        personMoveRequest.setPersonIdentification(personIdentification);

        PersonMoveResponse personMoveResponse = personenRegisterClient.identifyPerson(personMoveRequest, businessCaseId);

        Boolean personKnown = personMoveResponse.isPersonKnown();
        execution.setVariable("personKnown", personKnown);
        
        if(personKnown){
            BigInteger moveAllowedBigInteger = personMoveResponse.getMoveAllowed();
            if(moveAllowedBigInteger != null){
                // Wenn Person umzugsberechtigt ist, dann muss moveAllowed von
                // personMoveResponse den Wert 1 haben
                Boolean moveAllowed = moveAllowedBigInteger.equals(BigInteger.valueOf(1L));
                execution.setVariable("moveAllowed", moveAllowed);                

                //Wenn Person umzugsberechtigt ist, werden die mitumziehenden Personen 
                // ausgelesen, serializiert und in eine Prozessvariable gespeichert
                if(moveAllowed) {
                    List<PersonMoveResponse.RelatedPerson> relatives = personMoveResponse.getRelatedPerson();
                    ObjectValue relativesListSerialized = null;
                    Boolean relativesExist = false;

                    if(relatives != null && !relatives.isEmpty()) {
                        relativesListSerialized = Variables
                                .objectValue(relatives)
                                .serializationDataFormat(Variables.SerializationDataFormats.JSON)
                                .create();
                        relativesExist = true;
                    }

                    execution.setVariable("relativesListSerialized", relativesListSerialized);
                    execution.setVariable("relativesExist", relativesExist);
                }
            }
        }
    }
}