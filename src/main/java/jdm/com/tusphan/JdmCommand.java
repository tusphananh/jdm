package jdm.com.tusphan;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import io.micronaut.configuration.picocli.PicocliRunner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import java.net.URL;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

@Command(name = "jdm", description = "...", mixinStandardHelpOptions = true)
public class JdmCommand implements Runnable {

    @Command(name = "install", description = "Installs a package.")
    void install(
            @Option(names = { "-i", "--input" }, description = "Path to the input POM file") String inputFilePath,
            @Option(names = { "-o", "--output" }, description = "Path to the out POM file") String outputFilePath,
            @Parameters(paramLabel = "<package>", description = "The package to install") String packageName) {
        try {
            if (packageName.contains("@")) {
                // User specified a version
                System.out.println("Installing package: " + packageName);
            } else {
                // Fetch all available groupIds for the package
                System.out.println("Fetching available groupIds for package: " + packageName);
                String apiUrl = "https://search.maven.org/solrsearch/select?q=a:" + packageName + "&rows=50&wt=json";
                System.out.println("Fetching from: " + apiUrl);
                URL url = new URI(apiUrl).toURL();
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");

                if (connection.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONObject jsonResponse = new JSONObject(response.toString());
                    JSONArray docs = jsonResponse.getJSONObject("response").getJSONArray("docs");

                    // Collect all unique groupIds
                    Set<String> groupIds = new HashSet<>();
                    for (int i = 0; i < docs.length(); i++) {
                        JSONObject doc = docs.getJSONObject(i);
                        groupIds.add(doc.getString("g"));
                    }

                    if (groupIds.isEmpty()) {
                        System.out.println("No matching groupIds found for package: " + packageName);
                        return;
                    }

                    // Display available groupIds to the user
                    System.out.println("Available groupIds for package '" + packageName + "':");
                    int index = 1;
                    for (String groupId : groupIds) {
                        System.out.println(index + ". " + groupId);
                        index++;
                    }

                    // Prompt the user to select a groupId
                    System.out.print("Enter the number of the groupId you want to use: ");
                    Scanner scanner = new Scanner(System.in);
                    int choice = scanner.nextInt();

                    if (choice < 1 || choice > groupIds.size()) {
                        System.out.println("Invalid choice. Exiting.");
                        return;
                    }

                    // Get the selected groupId
                    String selectedGroupId = groupIds.toArray(new String[0])[choice - 1];

                    // Find the matching document and display details
                    for (int i = 0; i < docs.length(); i++) {
                        JSONObject doc = docs.getJSONObject(i);
                        if (doc.getString("g").equals(selectedGroupId)) {
                            String artifactId = doc.getString("a");
                            String latestVersion = doc.getString("latestVersion");

                            System.out.println("Installing package: ");
                            System.out.println("GroupId: " + selectedGroupId);
                            System.out.println("ArtifactId: " + artifactId);
                            System.out.println("Version: " + latestVersion);
                            System.out.println("File: " + inputFilePath);

                            addDepToPom(inputFilePath, selectedGroupId, artifactId, latestVersion, outputFilePath);
                            return;
                        }
                    }
                } else {
                    System.out.println("Failed to fetch details for package: " + packageName);
                }
            }
        } catch (Exception e) {
            System.out.println("An error occurred: " + e.getMessage());
        }
    }

    public void addDepToPom(String inputFilePath, String groupId, String artifactId, String version,
            String outputFilePath) {
        try {
            // Load the input pom.xml file
            if (inputFilePath == null || inputFilePath.isEmpty()) {
                inputFilePath = "pom.xml";
            }

            if (outputFilePath == null || outputFilePath.isEmpty()) {
                outputFilePath = inputFilePath;
            }

            File inputFile = new File(inputFilePath);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(inputFile);
            doc.getDocumentElement().normalize();

            // Check if the dependency already exists
            NodeList dependencies = doc.getElementsByTagName("dependency");
            boolean dependencyExists = false;
            for (int i = 0; i < dependencies.getLength(); i++) {
                Node dependency = dependencies.item(i);
                if (dependency.getNodeType() == Node.ELEMENT_NODE) {
                    Element depElement = (Element) dependency;
                    String existingGroupId = depElement.getElementsByTagName("groupId").item(0).getTextContent();
                    String existingArtifactId = depElement.getElementsByTagName("artifactId").item(0).getTextContent();
                    if (existingGroupId.equals(groupId) && existingArtifactId.equals(artifactId)) {
                        // Dependency exists, update the version
                        depElement.getElementsByTagName("version").item(0).setTextContent(version);
                        dependencyExists = true;
                        break;
                    }
                }
            }

            // If the dependency does not exist, add a new one
            if (!dependencyExists) {
                Node dependenciesNode = doc.getElementsByTagName("dependencies").item(0);
                Element newDependency = doc.createElement("dependency");

                Element newGroupId = doc.createElement("groupId");
                newGroupId.appendChild(doc.createTextNode(groupId));
                newDependency.appendChild(newGroupId);

                Element newArtifactId = doc.createElement("artifactId");
                newArtifactId.appendChild(doc.createTextNode(artifactId));
                newDependency.appendChild(newArtifactId);

                Element newVersion = doc.createElement("version");
                newVersion.appendChild(doc.createTextNode(version));
                newDependency.appendChild(newVersion);

                dependenciesNode.appendChild(newDependency);
            }

            // Save the updated pom.xml to the output file path
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(new File(outputFilePath));
            transformer.transform(source, result);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        PicocliRunner.run(JdmCommand.class, args);
    }

    @Override
    public void run() {
        System.out.println("Welcome to JDM! Use 'jdm install <package>' to install a package.");
    }
}
