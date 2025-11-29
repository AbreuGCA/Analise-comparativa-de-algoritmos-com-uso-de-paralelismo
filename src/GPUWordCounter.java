// GPUWordCounter.java
// Versão que tenta carregar kernels/match_kernel.cl; se não encontrar, usa programSource embutido.
// Compile com jocl-2.0.4.jar no classpath.

import org.jocl.*;
import java.nio.file.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class GPUWordCounter {

    private static final String programSourceEmbedded
            = "__kernel void count_matches(__global const uchar *text, const int textLen,"
            + "                            __global const uchar *pattern, const int patLen,"
            + "                            __global int *outCount) {"
            + "    int gid = get_global_id(0);"
            + "    if (gid + patLen > textLen) return;"
            + "    int i;"
            + "    for (i = 0; i < patLen; i++) {"
            + "        if (text[gid + i] != pattern[i]) return;"
            + "    }"
            + "    atomic_inc(outCount);"
            + "}";

    private static String loadKernelSource() {
        Path p = Paths.get("kernels", "match_kernel.cl");
        if (Files.exists(p)) {
            try {
                return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
            } catch (IOException e) {
                System.err.println("Falha ao ler kernels/match_kernel.cl, usando kernel embutido: " + e.getMessage());
            }
        }
        return programSourceEmbedded;
    }

    public static WordCountMain.Result countWithJOCL(byte[] textBytes, byte[] patternBytes) {
        CL.setExceptionsEnabled(true);
        long startTime = System.currentTimeMillis();

        String programSource = loadKernelSource();

        // Get platforms
        int[] numPlatforms = new int[1];
        CL.clGetPlatformIDs(0, null, numPlatforms);
        if (numPlatforms[0] == 0) {
            throw new RuntimeException("Nenhuma plataforma OpenCL encontrada.");
        }
        cl_platform_id[] platforms = new cl_platform_id[numPlatforms[0]];
        CL.clGetPlatformIDs(platforms.length, platforms, null);
        cl_platform_id platform = platforms[0];

        // Prefer GPU, fallback CPU
        cl_device_id[] devices = getDevices(platform, CL.CL_DEVICE_TYPE_GPU);
        if (devices == null) {
            devices = getDevices(platform, CL.CL_DEVICE_TYPE_CPU);
        }
        if (devices == null) {
            throw new RuntimeException("Nenhum device OpenCL encontrado.");
        }

        cl_device_id device = devices[0];

        cl_context context = CL.clCreateContext(null, 1, new cl_device_id[]{device}, null, null, null);
        cl_command_queue commandQueue = CL.clCreateCommandQueueWithProperties(context, device, null, null);

        int textLen = textBytes.length;
        int patLen = patternBytes.length;

        // Allocate host buffers
        cl_mem memText = CL.clCreateBuffer(context, CL.CL_MEM_READ_ONLY | CL.CL_MEM_COPY_HOST_PTR, textLen, Pointer.to(textBytes), null);
        cl_mem memPattern = CL.clCreateBuffer(context, CL.CL_MEM_READ_ONLY | CL.CL_MEM_COPY_HOST_PTR, patLen, Pointer.to(patternBytes), null);
        cl_mem memOut = CL.clCreateBuffer(context, CL.CL_MEM_READ_WRITE, Sizeof.cl_int, null, null);

        // Initialize output to zero
        int[] zero = new int[]{0};
        CL.clEnqueueWriteBuffer(commandQueue, memOut, CL.CL_TRUE, 0, Sizeof.cl_int, Pointer.to(zero), 0, null, null);

        // Build program
        cl_program program = CL.clCreateProgramWithSource(context, 1, new String[]{programSource}, null, null);
        int err = CL.clBuildProgram(program, 0, null, null, null, null);
        if (err != CL.CL_SUCCESS) {
            // tenta coletar log de build
            long[] logSize = new long[1];
            CL.clGetProgramBuildInfo(program, device, CL.CL_PROGRAM_BUILD_LOG, 0, null, logSize);
            byte[] logData = new byte[(int) logSize[0]];
            CL.clGetProgramBuildInfo(program, device, CL.CL_PROGRAM_BUILD_LOG, logData.length, Pointer.to(logData), null);
            String log = new String(logData, StandardCharsets.UTF_8);
            throw new RuntimeException("Erro no build do programa OpenCL: \n" + log);
        }

        cl_kernel kernel = CL.clCreateKernel(program, "count_matches", null);

        // Set args
        CL.clSetKernelArg(kernel, 0, Sizeof.cl_mem, Pointer.to(memText));
        CL.clSetKernelArg(kernel, 1, Sizeof.cl_int, Pointer.to(new int[]{textLen}));
        CL.clSetKernelArg(kernel, 2, Sizeof.cl_mem, Pointer.to(memPattern));
        CL.clSetKernelArg(kernel, 3, Sizeof.cl_int, Pointer.to(new int[]{patLen}));
        CL.clSetKernelArg(kernel, 4, Sizeof.cl_mem, Pointer.to(memOut));

        int globalWorkSize = Math.max(1, textLen - patLen + 1);
        long[] globalWork = new long[]{globalWorkSize};

        CL.clEnqueueNDRangeKernel(commandQueue, kernel, 1, null, globalWork, null, 0, null, null);
        CL.clFinish(commandQueue);

        int[] result = new int[1];
        CL.clEnqueueReadBuffer(commandQueue, memOut, CL.CL_TRUE, 0, Sizeof.cl_int, Pointer.to(result), 0, null, null);

        // Cleanup
        CL.clReleaseKernel(kernel);
        CL.clReleaseProgram(program);
        CL.clReleaseMemObject(memText);
        CL.clReleaseMemObject(memPattern);
        CL.clReleaseMemObject(memOut);
        CL.clReleaseCommandQueue(commandQueue);
        CL.clReleaseContext(context);

        long endTime = System.currentTimeMillis();
        return new WordCountMain.Result(result[0], endTime - startTime);
    }

    private static cl_device_id[] getDevices(cl_platform_id platform, long deviceType) {
        int[] numDevicesArray = new int[1];
        int status = CL.clGetDeviceIDs(platform, deviceType, 0, null, numDevicesArray);
        if (status != CL.CL_SUCCESS || numDevicesArray[0] == 0) {
            return null;
        }
        int numDevices = numDevicesArray[0];
        cl_device_id[] devices = new cl_device_id[numDevices];
        CL.clGetDeviceIDs(platform, deviceType, numDevices, devices, null);
        return devices;
    }
}
