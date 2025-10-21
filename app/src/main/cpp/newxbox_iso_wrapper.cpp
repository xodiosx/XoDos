#include <jni.h>
#include <string>
#include <fstream>
#include <vector>

extern "C" JNIEXPORT jboolean JNICALL
Java_com_termux_app_XoDosIsoRebuilder_rebuildXboxIso(JNIEnv *env, jobject thiz, jstring inputPath, jstring outputPath) {
    const char *inputC = env->GetStringUTFChars(inputPath, nullptr);
    const char *outputC = env->GetStringUTFChars(outputPath, nullptr);

    std::ifstream input(inputC, std::ios::binary);
    std::ofstream output(outputC, std::ios::binary);

    if (!input.is_open() || !output.is_open()) {
        env->ReleaseStringUTFChars(inputPath, inputC);
        env->ReleaseStringUTFChars(outputPath, outputC);
        return JNI_FALSE;
    }

    const size_t sectorSize = 2048;
    std::vector<char> buffer(sectorSize);

    while (input) {
        input.read(buffer.data(), sectorSize);
        std::streamsize bytesRead = input.gcount();
        if (bytesRead > 0) {
            output.write(buffer.data(), bytesRead);
            if (bytesRead < sectorSize) {
                std::vector<char> pad(sectorSize - bytesRead, 0);
                output.write(pad.data(), pad.size());
            }
        }
    }

    input.close();
    output.close();

    env->ReleaseStringUTFChars(inputPath, inputC);
    env->ReleaseStringUTFChars(outputPath, outputC);

    return JNI_TRUE;
}