package calebxzhou.rdi.mc.common2.mcp;

public interface RMcpGameConnector {
    boolean playerInWorld();

    RMcpTestData testData();

    RMcpPosData posData();

    RMcpStaringBlockData staringBlockData(boolean includeFluid);
}
