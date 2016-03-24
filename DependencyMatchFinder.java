import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.*;

import java.util.HashMap;
import java.util.HashSet;

public class DependencyMatchFinder {

    public HashSet<Triple<String, String, String>> findDependencyMatches(Question question) {


        HashMap<String, HashMap<String, String>> placeHolderEdges = new HashMap<String, HashMap<String, String>>();
        placeHolderEdges.put("out", new HashMap<String, String>());
        placeHolderEdges.put("in", new HashMap<String, String>());

        // get the edges going into and leaving @placeholder
        for (CoreMap sentence : question.questionAnnotation.get(CoreAnnotations.SentencesAnnotation.class)) {
            SemanticGraph sg = sentence.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class);
            for (IndexedWord iw : sg.vertexSet()) {
                if (iw.word().equals("@placeholder")) {
                    for (SemanticGraphEdge phe : sg.incomingEdgeList(iw)) {
                        placeHolderEdges.get("in").put(phe.getRelation().getShortName(), phe.getGovernor().word());
                    }
                    for (SemanticGraphEdge phe : sg.outgoingEdgeList(iw)) {
                        placeHolderEdges.get("out").put(phe.getRelation().getShortName(), phe.getDependent().word());
                    }
                }
            }
        }

        HashSet<Triple<String, String, String>> matchesFound = new HashSet<Triple<String, String, String>>();
        // go through the passage looking for matching edges
        for (CoreMap sentence : question.passageAnnotation.get(CoreAnnotations.SentencesAnnotation.class)) {
            SemanticGraph sg = sentence.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class);
            for (IndexedWord iw : sg.vertexSet()) {
                // skip if not of the form @entity55
                if (!question.entityMarkerToString.keySet().contains(iw.word()))
                    continue;
                for (SemanticGraphEdge se : sg.incomingEdgeList(iw)) {
                    String governorWord = se.getGovernor().word();
                    String relationName = se.getRelation().getShortName();
                    String potentialMatch = placeHolderEdges.get("in").get(relationName);
                    if (potentialMatch != null && potentialMatch.equals(governorWord))
                        matchesFound.add(new Triple(governorWord, relationName, iw.word()));
                }
                for (SemanticGraphEdge se : sg.outgoingEdgeList(iw)) {
                    String dependentWord = se.getDependent().word();
                    String relationName = se.getRelation().getShortName();
                    String potentialMatch = placeHolderEdges.get("out").get(relationName);
                    if (potentialMatch != null && potentialMatch.equals(dependentWord))
                        matchesFound.add(new Triple(iw.word(), relationName, dependentWord));
                }
            }
        }

        return matchesFound;
    }
}