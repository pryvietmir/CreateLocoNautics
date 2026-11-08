package com.lycoris.loconautics.content.controls.panel.visual;

import dev.engine_room.flywheel.api.model.IndexSequence;
import dev.engine_room.flywheel.api.model.Mesh;
import dev.engine_room.flywheel.api.vertex.MutableVertexList;
import org.joml.Vector4fc;

public record UvShiftedMesh(Mesh mesh, float du, float dv) implements Mesh {
    public int vertexCount() {
        return this.mesh.vertexCount();
    }

    public void write(MutableVertexList vertexList) {
        this.mesh.write(vertexList);

        for (int i = 0; i < this.mesh.vertexCount(); i++) {
            vertexList.u(i, vertexList.u(i) + this.du);
            vertexList.v(i, vertexList.v(i) + this.dv);
        }
    }

    public IndexSequence indexSequence() {
        return this.mesh.indexSequence();
    }

    public int indexCount() {
        return this.mesh.indexCount();
    }

    public Vector4fc boundingSphere() {
        return this.mesh.boundingSphere();
    }
}
