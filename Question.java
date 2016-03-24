import java.io.*;
import java.util.*;
import java.util.Properties;
import java.util.zip.*;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.semgraph.*;
import edu.stanford.nlp.util.*;

public class Question {

    /** answer to the question **/
    public String answer;
    /** the passage **/
    public String passage;
    /** annotation of passage **/
    public Annotation passageAnnotation;
    /** the query **/
    public String question;
    /** id for the question **/
    public String questionID;
    /** annotation of question **/
    public Annotation questionAnnotation;
    /** mapping of entity marker to real string **/
    public HashMap<String,String> entityMarkerToString;
    /** serializer for reading and writing annotations to disk **/
    public ProtobufAnnotationSerializer serializer;
    /** StanfordCoreNLP pipeline used to make annotations **/
    public StanfordCoreNLP pipeline;

    /**
     * given a path to a zipped, serialized Annotation load it *
     */
    public Annotation readSerializedAnnotation(String filePath) throws IOException, ClassNotFoundException {
        FileInputStream fis = new FileInputStream(filePath);
        GZIPInputStream gzs = new GZIPInputStream(fis);
        Annotation annotation = serializer.read(gzs).first();
        gzs.close();
        fis.close();
        return annotation;
    }

    /** write a gzipped annotation to disk **/
    public void writeSerializedAnnotation(String filePath, Annotation ann) throws IOException {
        FileOutputStream fos = new FileOutputStream(filePath);
        GZIPOutputStream gzs = new GZIPOutputStream(fos);
        serializer.write(ann, gzs);
        gzs.close();
        fos.close();
    }

    public static Pair<String,String> getEntityMarkerAndString(String kvEntry) {
        // get the marker
        String[] kvEntrySplit = kvEntry.split(":");
        String entityMarker = kvEntrySplit[0];
        // get the entity string
        String[] entityStringElements = Arrays.copyOfRange(kvEntrySplit, 1, kvEntrySplit.length);
        String entityString = String.join(":", entityStringElements);
        // return {"@entity14", "New York City"}
        return new Pair<String,String>(entityMarker, entityString);
    }

    public Question(String questionFilePath, Properties annProps) throws
            ClassNotFoundException, IOException {
        // get the passage id
        questionID = StringUtils.getBaseName(questionFilePath).split("\\.")[0];
        // get the lines of the question file
        List<String> questionLines = IOUtils.linesFromFile(questionFilePath);
        passage = questionLines.get(2);
        question = questionLines.get(4);
        answer = questionLines.get(6);
        entityMarkerToString = new HashMap<String,String>();
        for (int i = 8 ; i < questionLines.size() ; i++) {
            Pair<String,String> entityMarkerAndString = getEntityMarkerAndString(questionLines.get(i));
            entityMarkerToString.put(entityMarkerAndString.first(), entityMarkerAndString.second());
        }
        // set up the serializer
        serializer = new ProtobufAnnotationSerializer();
        // build passage and question annotations
        File passageAnnotationFile = new File(questionFilePath+".passage.ann");
        File questionAnnotationFile = new File(questionFilePath+".question.ann");
        if (passageAnnotationFile.exists() && questionAnnotationFile.exists()) {
            passageAnnotation = readSerializedAnnotation(passageAnnotationFile.getAbsolutePath());
            questionAnnotation = readSerializedAnnotation(questionAnnotationFile.getAbsolutePath());
        } else {
            pipeline = new StanfordCoreNLP(annProps);
            // build and save passage annotation
            passageAnnotation = new Annotation(passage);
            pipeline.annotate(passageAnnotation);
            writeSerializedAnnotation(questionFilePath + ".passage.ann.ser.gz", passageAnnotation);
            // build and save question annotation
            questionAnnotation = new Annotation(question);
            pipeline.annotate(questionAnnotation);
            writeSerializedAnnotation(questionFilePath + ".question.ann.ser.gz", questionAnnotation);
        }
    }

    public void printQuestion() {
        System.out.println("question id: ");
        System.out.println(questionID);
        System.out.println("");
        System.out.println("passage: ");
        System.out.println(passage);
        System.out.println();
        System.out.println("question: ");
        System.out.println(question);
        System.out.println("");
        System.out.println("annotations: ");
        System.out.println("");
        System.out.println("\t---");
        System.out.println("\tquestion");
        // print out pos info for question
        for (CoreMap sentence : questionAnnotation.get(CoreAnnotations.SentencesAnnotation.class)) {
            System.out.print("\t");
            for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                System.out.print("(" + token.word() + "," + token.get(CoreAnnotations.PartOfSpeechAnnotation.class) + ","
                        +token.get(CoreAnnotations.NamedEntityTagAnnotation.class)+") ");
            }
            System.out.println();
            System.out.println();
            System.out.println("\tdependency edges: ");
            String[] depEdges =
                    sentence.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class).toList().split("\n");
            for (String edge : depEdges) {
                System.out.println("\t"+edge);
            }
            System.out.println("");
        }
        // print out pos info for passage
        int sentenceCount = 0;
        for (CoreMap sentence : passageAnnotation.get(CoreAnnotations.SentencesAnnotation.class)) {
            System.out.println("\t---");
            System.out.println("\tpassage sentence "+sentenceCount);
            sentenceCount++;
            System.out.print("\t");
            for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                System.out.print("("+token.word()+","+token.get(CoreAnnotations.PartOfSpeechAnnotation.class)+","
                        +token.get(CoreAnnotations.NamedEntityTagAnnotation.class)+") ");
            }
            System.out.println();
            System.out.println();
            System.out.println("\tdependency edges: ");
            String[] depEdges =
                    sentence.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class).toList().split("\n");
            for (String edge : depEdges) {
                System.out.println("\t" + edge);
            }
            System.out.println("");
        }
    }

    public static void main(String[] args) throws ClassNotFoundException, IOException {
        Properties props = StringUtils.argsToProperties(args);
        String questionFilePath = props.getProperty("questionFilePath");
        Question sampleQuestion = new Question(questionFilePath, props);
        sampleQuestion.printQuestion();
        DependencyMatchFinder dependencyMatchFinder = new DependencyMatchFinder();
        List<Pair<String,SemanticGraphEdge>> matchingEdges =
                dependencyMatchFinder.findDependencyMatches(sampleQuestion);
        Collections.sort(matchingEdges);
        System.out.println("");
        System.out.println("matching edges found: ");
        System.out.println("");

        for (Pair<String,SemanticGraphEdge> entityAndEdge : matchingEdges) {
            String entityMarker = entityAndEdge.first();
            String entityName = sampleQuestion.entityMarkerToString.get(entityMarker);
            String matchingEdge = entityAndEdge.second().toString();
            System.out.println("\t" + entityAndEdge.toString());
        }
    }
}
