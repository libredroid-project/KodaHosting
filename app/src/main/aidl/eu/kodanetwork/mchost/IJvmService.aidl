package eu.kodanetwork.mchost;

interface IJvmService {
    int startJvm(String libJvmPath, String jarPath, int ramMb, String mainClass, String workDir);
    void killJvm();
    String getInitError();
}
