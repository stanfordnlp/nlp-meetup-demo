import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DependencyMatchFinder {

    public List<Pair<String,SemanticGraphEdge>> findDependencyMatches(Question question) {

        List<SemanticGraphEdge> placeHolderInEdges = new ArrayList<SemanticGraphEdge>();
        List<SemanticGraphEdge> placeHolderOutEdges = new ArrayList<SemanticGraphEdge>();

        // get the edges going into and leaving @placeholder
        for (CoreMap sentence : question.questionAnnotation.get(CoreAnnotations.SentencesAnnotation.class)) {
            SemanticGraph sg = sentence.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class);
            Set<IndexedWord> nodes = sg.vertexSet();
            for (IndexedWord iw : nodes) {
                if (iw.word().equals("@placeholder")) {
                    placeHolderInEdges = sg.incomingEdgeList(iw);
                    placeHolderOutEdges = sg.outgoingEdgeList(iw);
                }
            }
        }
        // go through, for each entity, check for matches
        HashMap<String,HashSet<SemanticGraphEdge>> matchingInEdges = new HashMap<String, HashSet<SemanticGraphEdge>>();
        HashMap<String,HashSet<SemanticGraphEdge>> matchingOutEdges = new HashMap<String, HashSet<SemanticGraphEdge>>();
        for (String entity : question.entityMarkerToString.keySet()) {
            matchingInEdges.put(entity, new HashSet<SemanticGraphEdge>());
            matchingOutEdges.put(entity, new HashSet<SemanticGraphEdge>());
        }
        // go through the passage and look for matching edges
        for (CoreMap sentence : question.passageAnnotation.get(CoreAnnotations.SentencesAnnotation.class)) {
            SemanticGraph sg = sentence.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class);
            Set<IndexedWord> nodes = sg.vertexSet();
            for (IndexedWord iw : nodes) {
                if (question.entityMarkerToString.keySet().contains(iw.word())) {
                    String entityMarker = iw.word();
                    // check for incoming edge matches
                    List<SemanticGraphEdge> inEdges = sg.incomingEdgeList(iw);
                    for (SemanticGraphEdge ie : inEdges) {
                        for (SemanticGraphEdge phie : placeHolderInEdges) {
                            if ((ie.getGovernor().word().equals(phie.getGovernor().word())) &&
                                    ie.getRelation().getShortName().equals(phie.getRelation().getShortName())) {
                                matchingInEdges.get(entityMarker).add(phie);
                            }
                        }
                    }
                    // check for outgoing edge matches
                    List<SemanticGraphEdge> outEdges = sg.outgoingEdgeList(iw);
                    for (SemanticGraphEdge oe : outEdges) {
                        for (SemanticGraphEdge phoe : placeHolderOutEdges) {
                            if ((oe.getDependent().word().equals(phoe.getDependent().word())) &&
                                    oe.getRelation().getShortName().equals(phoe.getRelation().getShortName())) {
                                matchingOutEdges.get(entityMarker).add(phoe);
                            }
                        }
                    }
                }
            }
        }
        ArrayList<Pair<String,SemanticGraphEdge>> dependencyMatches =
                new ArrayList<Pair<String,SemanticGraphEdge>>();
        for (String entity : matchingInEdges.keySet()) {
            for (SemanticGraphEdge se : matchingInEdges.get(entity)) {
                Pair<String, SemanticGraphEdge> entityAndMatchingEdge =
                        new Pair<String,SemanticGraphEdge>(entity, se);
                dependencyMatches.add(entityAndMatchingEdge);
            }
        }
        for (String entity : matchingOutEdges.keySet()) {
            for (SemanticGraphEdge se : matchingOutEdges.get(entity)) {
                Pair<String, SemanticGraphEdge> entityAndMatchingEdge =
                        new Pair<String,SemanticGraphEdge>(entity, se);
                dependencyMatches.add(entityAndMatchingEdge);
            }
        }
        return dependencyMatches;
    }
}