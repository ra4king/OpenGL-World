package com.ra4king.fps.renderers;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.*;

import java.nio.ByteBuffer;

import org.lwjgl.BufferUtils;

import com.ra4king.fps.renderers.WorldRenderer.DrawElementsIndirectCommand;
import com.ra4king.fps.world.Chunk;
import com.ra4king.fps.world.Chunk.Block;
import com.ra4king.fps.world.Chunk.BlockType;
import com.ra4king.fps.world.Chunk.ChunkModifiedCallback;
import com.ra4king.opengl.util.Stopwatch;
import com.ra4king.opengl.util.Utils;

import net.indiespot.struct.cp.Struct;
import net.indiespot.struct.cp.TakeStruct;

public class ChunkRenderer implements ChunkModifiedCallback {
	private Chunk chunk;
	private int dataVBO;
	private int chunkNumOffset;
	
	private ByteBuffer buffer;
	private Block[] compact;
	private int blockCount;
	
	private boolean chunkModified = true;
	
	public static final int CHUNK_DATA_SIZE = Chunk.TOTAL_CUBES * Struct.sizeof(Block.class);

	public ChunkRenderer(Chunk chunk, int dataVBO, int chunkNumOffset) {
		this.chunk = chunk;
		this.dataVBO = dataVBO;
		this.chunkNumOffset = chunkNumOffset;
		
		chunk.setCallback(this);
		
		buffer = BufferUtils.createByteBuffer(CHUNK_DATA_SIZE);
		compact = Struct.map(Block.class, buffer);
		
		for(Block b : compact)
			b.setType(BlockType.AIR);
	}
	
	public Chunk getChunk() {
		return chunk;
	}
	
	@Override
	@TakeStruct
	public Block chunkAdd(int x, int y, int z, BlockType type) {
		return compact[blockCount++].init(chunk, x, y, z, type);
	}
	
	@Override
	public void chunkModified() {
		chunkModified = true;
	}
	
	private boolean onlyOnce = true;
	
	private void updateCompactArray() {
		for(int a = compact.length - 1; a >= blockCount; a--) {
			if(compact[a].getType() != BlockType.AIR || !compact[a].isSurrounded(chunk)) {
				if(a > blockCount) {
					Block src = compact[blockCount++];
					Block dest = compact[a++];
					Struct.swap(Block.class, src, dest);
					
					Block[] blocks = chunk.getBlocks();
					blocks[chunk.posToArrayIndex(src.getX() % Chunk.CHUNK_CUBE_WIDTH, src.getY() % Chunk.CHUNK_CUBE_HEIGHT, src.getZ() % Chunk.CHUNK_CUBE_DEPTH)] = src;
					blocks[chunk.posToArrayIndex(dest.getX() % Chunk.CHUNK_CUBE_WIDTH, dest.getY() % Chunk.CHUNK_CUBE_HEIGHT, dest.getZ() % Chunk.CHUNK_CUBE_DEPTH)] = dest;
				}
				else
					blockCount++;
			}
		}
		
		for(int a = blockCount - 1; a >= 0; a--) {
			if(compact[a].getType() == BlockType.AIR || compact[a].isSurrounded(chunk)) {
				if(a < blockCount - 1) {
					Block src = compact[--blockCount];
					Block dest = compact[a++];
					Struct.swap(Block.class, src, dest);
					
					Block[] blocks = chunk.getBlocks();
					blocks[chunk.posToArrayIndex(src.getX() % Chunk.CHUNK_CUBE_WIDTH, src.getY() % Chunk.CHUNK_CUBE_HEIGHT, src.getZ() % Chunk.CHUNK_CUBE_DEPTH)] = src;
					blocks[chunk.posToArrayIndex(dest.getX() % Chunk.CHUNK_CUBE_WIDTH, dest.getY() % Chunk.CHUNK_CUBE_HEIGHT, dest.getZ() % Chunk.CHUNK_CUBE_DEPTH)] = dest;
				}
				else
					blockCount--;
			}
		}
		
		if(onlyOnce) {
			Block src = compact[0];
			Block dest = compact[1];
			Struct.swap(Block.class, src, dest);
			
			Block[] blocks = chunk.getBlocks();
			blocks[chunk.posToArrayIndex(src.getX() % Chunk.CHUNK_CUBE_WIDTH, src.getY() % Chunk.CHUNK_CUBE_HEIGHT, src.getZ() % Chunk.CHUNK_CUBE_DEPTH)] = src;
			blocks[chunk.posToArrayIndex(dest.getX() % Chunk.CHUNK_CUBE_WIDTH, dest.getY() % Chunk.CHUNK_CUBE_HEIGHT, dest.getZ() % Chunk.CHUNK_CUBE_DEPTH)] = dest;
			
			onlyOnce = false;
		}
	}
	
	// private void printBuffer(ByteBuffer buffer) {
	// for(int a = 0; a < buffer.limit(); a += Struct.sizeof(Block.class)) {
	// System.out.printf("(%d,%d,%d) type %d\n", buffer.getInt(a), buffer.getInt(a + 4), buffer.getInt(a + 8), buffer.getInt(12));
	// }
	// }
	
	private void updateVBO() {
		Stopwatch.start("Update Compact Array");
		updateCompactArray();
		Stopwatch.stop();

	Stopwatch.start("UpdateVBO");
		
		final int DATA_SIZE = blockCount * Struct.sizeof(Block.class);

		glBindBuffer(GL_ARRAY_BUFFER, dataVBO);
		ByteBuffer mappedBuffer = glMapBufferRange(GL_ARRAY_BUFFER, chunkNumOffset * CHUNK_DATA_SIZE, DATA_SIZE,
				GL_MAP_WRITE_BIT | GL_MAP_INVALIDATE_RANGE_BIT | GL_MAP_UNSYNCHRONIZED_BIT, null);
		if(mappedBuffer == null) {
			Utils.checkGLError("mapped buffer");
			System.exit(0);
		}
		
		buffer.position(0).limit(DATA_SIZE);
		mappedBuffer.put(buffer);
		
		glUnmapBuffer(GL_ARRAY_BUFFER);
		
		glBindBuffer(GL_ARRAY_BUFFER, 0);
		
		Stopwatch.stop();
		
		chunkModified = false;
	}
	
	public void printVBO() {
		glBindBuffer(GL_ARRAY_BUFFER, dataVBO);
		ByteBuffer mappedBuffer = glMapBufferRange(GL_ARRAY_BUFFER, chunkNumOffset * CHUNK_DATA_SIZE, blockCount * Struct.sizeof(Block.class),
				GL_MAP_READ_BIT, null);
		for(int a = 0; a < mappedBuffer.limit(); a += Struct.sizeof(Block.class)) {
			System.out.printf("(%d,%d,%d) type %d\n", mappedBuffer.getInt(a), mappedBuffer.getInt(a + 4), mappedBuffer.getInt(a + 8), mappedBuffer.getInt(a + 12));
		}
		glUnmapBuffer(GL_ARRAY_BUFFER);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
	}
	
	public int getLastCubeRenderCount() {
		return blockCount;
	}
	
	public void render(DrawElementsIndirectCommand command) {
		if(chunkModified) {
			updateVBO();
		}
		
		command.instanceCount = blockCount;
		command.baseInstance = chunkNumOffset * Chunk.TOTAL_CUBES;
	}
}