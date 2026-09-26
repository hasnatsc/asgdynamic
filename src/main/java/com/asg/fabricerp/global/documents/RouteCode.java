package com.asg.fabricerp.global.documents;

/** The five ways a fabric type is made - fab_process_routes.route_code. */
public enum RouteCode {
    GREIGE("Greige"),
    YARN_DYED_GREIGE("Yarn-dyed greige"),
    DENIM_GREIGE("Denim greige"),
    PIECE_DYED("Piece-dyed"),
    FINISHED("Finished yarn-dyed and denim");

    private final String label;
    RouteCode(String label) { this.label = label; }
    public String label() { return label; }
}
