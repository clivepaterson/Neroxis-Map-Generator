package com.faforever.neroxis.map;

import com.faforever.neroxis.util.Vector2f;
import com.faforever.neroxis.util.Vector3f;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Data
public strictfp class Spawn extends Marker {
    private Vector2f noRushOffset;
    private int teamID;

    public Spawn(String id, Vector2f position, Vector2f noRushOffset, int teamID) {
        this(id, new Vector3f(position), noRushOffset, teamID);
    }

    public Spawn(String id, Vector3f position, Vector2f noRushOffset, int teamID) {
        super(id, position);
        this.noRushOffset = noRushOffset;
        this.teamID = teamID;
    }
}