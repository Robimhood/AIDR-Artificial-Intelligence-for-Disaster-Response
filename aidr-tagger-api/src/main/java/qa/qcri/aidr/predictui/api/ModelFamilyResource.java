/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package qa.qcri.aidr.predictui.api;

import java.util.Collections;
import java.util.List;

import qa.qcri.aidr.common.logging.ErrorLog;
import qa.qcri.aidr.dbmanager.dto.ModelFamilyDTO;
import qa.qcri.aidr.predictui.util.ResponseWrapper;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.Path;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.log4j.Logger;
import qa.qcri.aidr.dbmanager.dto.taggerapi.TaggersForCodes;
import qa.qcri.aidr.dbmanager.dto.taggerapi.TaggersForCodesRequest;

import qa.qcri.aidr.predictui.entities.ModelFamily;
import qa.qcri.aidr.predictui.facade.ModelFamilyFacade;
import static qa.qcri.aidr.predictui.util.ConfigProperties.getProperty;

/**
 * REST Web Service
 *
 * @author Imran
 */
@Path("/modelfamily")
@Stateless
public class ModelFamilyResource {

    @Context
    private UriInfo context;
    @EJB
    private ModelFamilyFacade modelFamilyLocalEJB;

    //private static Logger logger = Logger.getLogger(ModelFamilyResource.class);
    private static Logger logger = Logger.getLogger("aidr-tagger-api");
    private static ErrorLog elog = new ErrorLog();
    
    public ModelFamilyResource() {
    }
    
    @GET
    @Produces("application/json")
    @Path("/all")
    public Response getAllModelFamilies() {
        List<ModelFamilyDTO> modelFamilyList = modelFamilyLocalEJB.getAllModelFamilies();
        ResponseWrapper response = new ResponseWrapper();
        response.setMessage("SUCCESS");
        response.setModelFamilies(modelFamilyList);
        return Response.ok(response).build();
    }
    
    @GET
    @Produces("application/json")
    @Path("/crisis/{id}")
    public Response getAllModelFamiliesByCrisisID(@PathParam("id") Long crisisID) {
        List<ModelFamilyDTO> modelFamilyList = modelFamilyLocalEJB.getAllModelFamiliesByCrisis(crisisID);
        ResponseWrapper response = new ResponseWrapper();
        response.setMessage("SUCCESS");
        response.setModelFamilies(modelFamilyList);
        return Response.ok(response).build();
    }
    
   @GET
   @Produces(MediaType.APPLICATION_JSON)
   @Path("{id}")
   public Response getModelByID(@PathParam("id") Long id){
       ModelFamilyDTO modelFamily = modelFamilyLocalEJB.getModelFamilyByID(id);
       return Response.ok(modelFamily).build();
   }
   
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response addCrisisAttribute(ModelFamilyDTO modelFamilyDTO) {
//      because ModelFamily has @XmlTransient annotation for crises and crisis was always null
        try {
            modelFamilyLocalEJB.addCrisisAttribute(modelFamilyDTO);
            ModelFamilyDTO modelFamily = modelFamilyLocalEJB.getModelFamilyByID(modelFamilyDTO.getModelFamilyId());
            return Response.ok(modelFamily).build();
        } catch (RuntimeException e) {
            logger.error("Error while adding Crisis attribute. Possible causes could be duplication of primary key, incomplete data, incompatible data format: " + modelFamilyDTO.getCrisisDTO().getCode() + "," + modelFamilyDTO.getNominalAttributeDTO().getCode());
            logger.error(elog.toStringException(e));
        	return Response.ok("Error while adding Crisis attribute. Possible causes could be duplication of primary key, incomplete data, incompatible data format.").build();
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/taggers-by-codes")
    public Response getTaggersByCodes(TaggersForCodesRequest codesRequest) {
        List<TaggersForCodes> taggersByCodes = modelFamilyLocalEJB.getTaggersByCodes(codesRequest.getCodes());
        if (taggersByCodes.isEmpty() || taggersByCodes == null){
            taggersByCodes = Collections.emptyList();
        }
        ResponseWrapper response = new ResponseWrapper();
        response.setTaggersForCodes(taggersByCodes);
        return Response.ok(response).build();
    }

    /*
    private ModelFamily transformModelFamilyDTO(ModelFamilyDTO modelFamilyDTO){
        ModelFamily modelFamily = new ModelFamily();
        modelFamily.setCrisis(modelFamilyDTO.getCrisis());
        modelFamily.setNominalAttribute(modelFamilyDTO.getNominalAttribute());
        modelFamily.setIsActive(modelFamilyDTO.getIsActive());
        return modelFamily;
    }
    */
    
    @DELETE
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteAttribute(@PathParam("id") Long modelFamilyID) {
        try {
            modelFamilyLocalEJB.deleteModelFamily(modelFamilyID);
        } catch (RuntimeException e) {
            logger.error("Error while deleting Classifier for modelFamily: " + modelFamilyID);
            logger.error(elog.toStringException(e));
            
        	return Response.ok(
                    new ResponseWrapper(getProperty("STATUS_CODE_FAILED"),
                    "Error while deleting Classifier.")).build();
        }
        return Response.ok(new ResponseWrapper(getProperty("STATUS_CODE_SUCCESS"))).build();
    }
    
}
