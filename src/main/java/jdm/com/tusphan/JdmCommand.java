package jdm.com.tusphan;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;

import org.json.JSONArray;
import org.json.JSONObject;
import io.micronaut.configuration.picocli.PicocliRunner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import java.net.URL;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

@Command(name = "jdm", description = "...", mixinStandardHelpOptions = true)
public class JdmCommand implements Runnable {

    @Command(name = "install", description = "Installs a package.")
    void install(@Parameters(paramLabel = "<package>", description = "The package to install") String packageName) {
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

    public static void main(String[] args) throws Exception {
        PicocliRunner.run(JdmCommand.class, args);
    }

    @Override
    public void run() {
        System.out.println("Welcome to JDM! Use 'jdm install <package>' to install a package.");
    }
}
