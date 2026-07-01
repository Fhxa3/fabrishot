package me.ramidzkh.fabrishot.config;

/**
 * JPEG chroma subsampling mode — controls colour resolution vs file size.
 * Higher values discard more colour information for smaller files.
 */
public enum JpgColorSampling {
    YUV444("4:4:4"),
    YUV422("4:2:2"),
    YUV420("4:2:0");

    private final String label;

    JpgColorSampling(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
