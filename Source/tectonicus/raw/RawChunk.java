/*
 * Copyright (c) 2012-2014, John Campbell and other contributors.  All rights reserved.
 *
 * This file is part of Tectonicus. It is subject to the license terms in the LICENSE file found in
 * the top-level directory of this distribution.  The full list of project contributors is contained
 * in the AUTHORS file found in the same location.
 *
 */

package tectonicus.raw;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.DatatypeConverter;

import org.jnbt.ByteArrayTag;
import org.jnbt.ByteTag;
import org.jnbt.CompoundTag;
import org.jnbt.IntTag;
import org.jnbt.ListTag;
import org.jnbt.NBTInputStream;
import org.jnbt.NBTInputStream.Compression;
import org.jnbt.ShortTag;
import org.jnbt.StringTag;
import org.jnbt.Tag;
import org.json.JSONObject;

import tectonicus.BlockIds;
import tectonicus.ChunkCoord;

public class RawChunk
{
	public static final int WIDTH = 16;
	public static final int HEIGHT = 256;
	public static final int DEPTH = 16;
	
	public static final int MC_REGION_HEIGHT = 128;
	
	public static final int SECTION_WIDTH = 16;
	public static final int SECTION_HEIGHT = 16;
	public static final int SECTION_DEPTH = 16;
	
	public static final int MAX_LIGHT = 16;
	
	private static final int MAX_SECTIONS = HEIGHT / SECTION_HEIGHT;
	
	private byte[][] biomes;
	
	private Section[] sections;
	
	private int blockX, blockY, blockZ;
	
	private ArrayList<RawSign> signs;
	private ArrayList<TileEntity> flowerPots;
	private ArrayList<TileEntity> paintings;
	private ArrayList<TileEntity> skulls;
	private ArrayList<TileEntity> beacons;
	private ArrayList<TileEntity> banners;
	private ArrayList<TileEntity> itemFrames;
	
	private Map<String, Object> filterData = new HashMap<String, Object>();
	
	public RawChunk()
	{
		clear();
	}
	
	public RawChunk(File file) throws Exception
	{
		FileInputStream fileIn = new FileInputStream(file);
		init(fileIn, Compression.Gzip);
	}
	
	public RawChunk(InputStream in, Compression compression) throws Exception
	{
		init(in, compression);
	}
	
	public void setFilterMetadata(String id, Object data)
	{
		this.filterData.put(id, data);
	}
	public void removeFilterMetadata(String id)
	{
		this.filterData.remove(id);
	}
	public Object getFilterMetadata(String id)
	{
		return this.filterData.get(id);
	}
	
	private void clear()
	{
		signs = new ArrayList<RawSign>();
		flowerPots = new ArrayList<TileEntity>();
		paintings = new ArrayList<TileEntity>();
		skulls = new ArrayList<TileEntity>();
		beacons = new ArrayList<TileEntity>();
		banners = new ArrayList<TileEntity>();
		itemFrames = new ArrayList<TileEntity>();
		
		sections = new Section[MAX_SECTIONS];
	}
	
	private void init(InputStream in, Compression compression) throws Exception
	{
		clear();
		
		NBTInputStream nbtIn = null;
		try
		{
			nbtIn = new NBTInputStream(in, compression);
			
			Tag tag = nbtIn.readTag();
			if (tag instanceof CompoundTag)
			{
				CompoundTag root = (CompoundTag)tag;
				
				CompoundTag level = NbtUtil.getChild(root, "Level", CompoundTag.class);
				if (level != null)
				{
					blockX = blockY = blockZ = 0;
					
					IntTag xPosTag = NbtUtil.getChild(level, "xPos", IntTag.class);
					if (xPosTag != null)
						blockX = xPosTag.getValue().intValue();
					
					IntTag zPosTag = NbtUtil.getChild(level, "zPos", IntTag.class);
					if (zPosTag != null)
						blockZ = zPosTag.getValue().intValue();
					
					ListTag sections = NbtUtil.getChild(level, "Sections", ListTag.class);
					if (sections != null)
					{
						// Parse as anvil format
						parseAnvilData(level);
					}
					else
					{
						// Parse as McRegion format
						parseMcRegionData(level);
					}
					
					ListTag entitiesTag = NbtUtil.getChild(level, "Entities", ListTag.class);
					if (entitiesTag != null)
					{
						for (Tag t : entitiesTag.getValue())
						{
							if (t instanceof CompoundTag)
							{
								CompoundTag entity = (CompoundTag)t;
								
								StringTag idTag = NbtUtil.getChild(entity, "id", StringTag.class);
								if (idTag.getValue().endsWith("Painting"))
								{
									StringTag motiveTag = NbtUtil.getChild(entity, "Motive", StringTag.class);
									IntTag xTag = NbtUtil.getChild(entity, "TileX", IntTag.class);
									IntTag yTag = NbtUtil.getChild(entity, "TileY", IntTag.class);
									IntTag zTag = NbtUtil.getChild(entity, "TileZ", IntTag.class);
									ByteTag dir = NbtUtil.getChild(entity, "Direction", ByteTag.class);
									boolean is18 = false;
									if (dir == null){
										dir = NbtUtil.getChild(entity, "Facing", ByteTag.class);
										is18 = true;
									}
									
									int x = xTag.getValue();
									final int y = yTag.getValue();
									int z = zTag.getValue();
									
									if (is18 && dir.getValue() == 0){
										z = zTag.getValue() - 1;
									}
									else if (is18 && dir.getValue() == 1){
										x = xTag.getValue() + 1;
									}
									else if (is18 && dir.getValue() == 2){
										z = zTag.getValue() + 1;
									}
									else if (is18 && dir.getValue() == 3){
										x = xTag.getValue() - 1;
									}
									
									final int localX = x-(blockX*WIDTH);
									final int localY  = y-(blockY*HEIGHT);
									final int localZ = z-(blockZ*DEPTH);
									
									//System.out.println("Motive: " + motiveTag.getValue() + " Direction: " + dir.getValue() + " XYZ: " + x + ", " + y + ", " + z + " Local XYZ: " + localX +
											//", " + localY + ", " + localZ);
									paintings.add(new TileEntity(-1, 0, x, y, z, localX, localY, localZ, motiveTag.getValue(), dir.getValue()));
								}
								else if (idTag.getValue().equals("ItemFrame"))
								{
									IntTag xTag = NbtUtil.getChild(entity, "TileX", IntTag.class);
									IntTag yTag = NbtUtil.getChild(entity, "TileY", IntTag.class);
									IntTag zTag = NbtUtil.getChild(entity, "TileZ", IntTag.class);
									ByteTag dir = NbtUtil.getChild(entity, "Direction", ByteTag.class);
									boolean is18 = false;
									if (dir == null){
										dir = NbtUtil.getChild(entity, "Facing", ByteTag.class);
										is18 = true;
									}
									
									String item = "";
									Map<String, Tag> map = entity.getValue();
									CompoundTag itemTag = (CompoundTag) map.get("Item");
									if(itemTag != null)
									{
										ShortTag itemIdTag = NbtUtil.getChild(itemTag, "id", ShortTag.class);
										if (itemIdTag == null)
										{
											StringTag stringItemIdTag = NbtUtil.getChild(itemTag, "id", StringTag.class);
											item = stringItemIdTag.getValue();
										}
										else
										{
											if (itemIdTag.getValue() == 358)
												item = "minecraft:filled_map";
										}
									}
									

									int x = xTag.getValue();
									final int y = yTag.getValue();
									int z = zTag.getValue();
									
									if (is18 && dir.getValue() == 0){
										z = zTag.getValue() - 1;
									}
									else if (is18 && dir.getValue() == 1){
										x = xTag.getValue() + 1;
									}
									else if (is18 && dir.getValue() == 2){
										z = zTag.getValue() + 1;
									}
									else if (is18 && dir.getValue() == 3){
										x = xTag.getValue() - 1;
									}
									
									final int localX = x-(blockX*WIDTH);
									final int localY  = y-(blockY*HEIGHT);
									final int localZ = z-(blockZ*DEPTH);
									
									//System.out.println(" Direction: " + dir.getValue() + " XYZ: " + x + ", " + y + ", " + z + " Local XYZ: " + localX +
											//", " + localY + ", " + localZ);
									
									itemFrames.add(new TileEntity(-2, 0, x, y, z, localX, localY, localZ, item, dir.getValue()));
								}
							}
						}
					}
					
					ListTag tileEntitiesTag = NbtUtil.getChild(level, "TileEntities", ListTag.class);
					if (tileEntitiesTag != null)
					{
						for (Tag t : tileEntitiesTag.getValue())
						{
							if (t instanceof CompoundTag)
							{
								CompoundTag entity = (CompoundTag)t;
								
								StringTag idTag = NbtUtil.getChild(entity, "id", StringTag.class);
								IntTag xTag = NbtUtil.getChild(entity, "x", IntTag.class);
								IntTag yTag = NbtUtil.getChild(entity, "y", IntTag.class);
								IntTag zTag = NbtUtil.getChild(entity, "z", IntTag.class);
								
								if (idTag != null && xTag != null && yTag != null && zTag != null)
								{
									String id = idTag.getValue();
									if (id.equals("Sign"))
									{
										StringTag text1Tag = NbtUtil.getChild(entity, "Text1", StringTag.class);
										StringTag text2Tag = NbtUtil.getChild(entity, "Text2", StringTag.class);
										StringTag text3Tag = NbtUtil.getChild(entity, "Text3", StringTag.class);
										StringTag text4Tag = NbtUtil.getChild(entity, "Text4", StringTag.class);

										String text1 = text1Tag.getValue().replaceAll("^\"|\"$", "");  //This regex removes begin and end double quotes
										if (text1 == null || text1.equals("null"))
											text1 = "";
										
										String text2 = text2Tag.getValue().replaceAll("^\"|\"$", "");
										if (text2 == null || text2.equals("null"))
											text2 = "";
										
										String text3 = text3Tag.getValue().replaceAll("^\"|\"$", "");
										if (text3 == null || text3.equals("null"))
											text3 = "";
										
										String text4 = text4Tag.getValue().replaceAll("^\"|\"$", "");
										if (text4 == null || text4.equals("null"))
											text4 = "";
										
										final int x = xTag.getValue();
										final int y = yTag.getValue();
										final int z = zTag.getValue();
										
										final int localX = x-(blockX*WIDTH);
										final int localY  = y-(blockY*HEIGHT);
										final int localZ = z-(blockZ*DEPTH);
										
										final int blockId = getBlockId(localX, localY, localZ);
										final int data = getBlockData(localX, localY, localZ);
										
										signs.add( new RawSign( blockId, data,
																x, y, z,
																localX, localY, localZ,
																text1, text2, text3, text4) );
									}
									else if (id.equals("FlowerPot"))
									{
										IntTag dataTag = NbtUtil.getChild(entity, "Data", IntTag.class);
										IntTag itemTag = NbtUtil.getChild(entity, "Item", IntTag.class);
										final int item;
										if(itemTag == null)
										{
											StringTag stringIdTag = NbtUtil.getChild(entity, "Item", StringTag.class);
											if (stringIdTag.getValue().equals("minecraft:sapling"))
												item = 6;
											else if (stringIdTag.getValue().equals("minecraft:red_flower"))
												item = 38;
											else
												item = 0;
										}
										else
										{
											item = itemTag.getValue();
										}
										
										final int x = xTag.getValue();
										final int y = yTag.getValue();
										final int z = zTag.getValue();
										
										final int localX = x-(blockX*WIDTH);
										final int localY  = y-(blockY*HEIGHT);
										final int localZ = z-(blockZ*DEPTH);

										final int blockData = getBlockData(localX, localY, localZ);
										
										final int itemData = dataTag.getValue();
										
										flowerPots.add(new TileEntity(0, blockData, x, y, z, localX, localY, localZ, itemData, item));
									}
									else if (id.equals("Skull"))
									{
										ByteTag skullType = NbtUtil.getChild(entity, "SkullType", ByteTag.class);
										ByteTag rot = NbtUtil.getChild(entity, "Rot", ByteTag.class);
										
										StringTag nameTag = null;
										StringTag playerId = null;
										String name = "";
										String UUID = "";
										String textureURL = "";
										StringTag extraType = NbtUtil.getChild(entity, "ExtraType", StringTag.class);
										CompoundTag owner = NbtUtil.getChild(entity, "Owner", CompoundTag.class);
										if(owner != null)
										{
											nameTag = NbtUtil.getChild(owner, "Name", StringTag.class);
											name = nameTag.getValue();
											playerId = NbtUtil.getChild(owner, "Id", StringTag.class);
											UUID = playerId.getValue().replace("-", "");
											
											// Get skin URL
											CompoundTag properties = NbtUtil.getChild(owner, "Properties", CompoundTag.class);
											ListTag textures = NbtUtil.getChild(properties, "textures", ListTag.class);
											CompoundTag tex = NbtUtil.getChild(textures, 0, CompoundTag.class);
											StringTag value = NbtUtil.getChild(tex, "Value", StringTag.class);
											byte[] decoded = DatatypeConverter.parseBase64Binary(value.getValue());
								            JSONObject obj = new JSONObject(new String(decoded, "UTF-8"));
								            textureURL = obj.getJSONObject("textures").getJSONObject("SKIN").getString("url");
										}
										else if (extraType != null && !(extraType.getValue().equals("")))
										{
											name = UUID = extraType.getValue();
											textureURL = "http://www.minecraft.net/skin/"+extraType.getValue()+".png";
										}
										
										final int x = xTag.getValue();
										final int y = yTag.getValue();
										final int z = zTag.getValue();
										
										final int localX = x-(blockX*WIDTH);
										final int localY  = y-(blockY*HEIGHT);
										final int localZ = z-(blockZ*DEPTH);
										
										skulls.add(new TileEntity( skullType.getValue(), rot.getValue(), x, y, z, localX, localY, localZ, name, UUID, textureURL, null));
									}
									else if (id.equals("Beacon"))
									{
										IntTag levels = NbtUtil.getChild(entity, "Levels", IntTag.class);
										
										final int x = xTag.getValue();
										final int y = yTag.getValue();
										final int z = zTag.getValue();
										
										final int localX = x-(blockX*WIDTH);
										final int localY  = y-(blockY*HEIGHT);
										final int localZ = z-(blockZ*DEPTH);
										
										beacons.add(new TileEntity(0, levels.getValue(), x, y, z, localX, localY, localZ, 0, 0));
									}
									else if (id.equals("Banner"))
									{
										IntTag base = NbtUtil.getChild(entity, "Base", IntTag.class);
										
										final int x = xTag.getValue();
										final int y = yTag.getValue();
										final int z = zTag.getValue();
										
										final int localX = x-(blockX*WIDTH);
										final int localY  = y-(blockY*HEIGHT);
										final int localZ = z-(blockZ*DEPTH);
										
										banners.add(new TileEntity(0, base.getValue(), x, y, z, localX, localY, localZ, 0, 0));
									}
								//	else if (id.equals("Furnace"))
								//	{
								//		
								//	}
								//	else if (id.equals("MobSpawner"))
								//	{
								//		
								//	}
								//	else if (id.equals("Chest"))
								//	{
								//		
								//	}
								}
							}
						}
					}
					
				//	LongTag lastUpdateTag =
				//		NbtUtil.getChild(level, "LastUpdate", LongTag.class);
					
				//	ByteTag terrainPopulatedTag = 
				//		NbtUtil.getChild(level, "TerrainPopulated", ByteTag.class);
				}
			}
		}
		finally
		{
			if (nbtIn != null)
				nbtIn.close();
			if (in != null)
				in.close();
		}
		
		/* Old debug: put bricks in the corner of every chunk
		for (int y=0; y<HEIGHT; y++)
		{
			if (blockIds[0][y][0] != BlockIds.AIR)
			{
				if (signs.size() > 0)
					blockIds[0][y][0] = BlockIds.DIAMOND_BLOCK;
				else
					blockIds[0][y][0] = BlockIds.BRICK;
			}
		}
		*/
	}
	
	
	private void parseAnvilData(CompoundTag level)
	{
		ListTag sectionsList = NbtUtil.getChild(level, "Sections", ListTag.class);
		// sections shouldn't be null here
		
		List<Tag> list = sectionsList.getValue();
		for (Tag t : list)
		{
			if (!(t instanceof CompoundTag))
				continue;
				
			CompoundTag compound = (CompoundTag)t;
			
			final int sectionY = NbtUtil.getByte(compound, "Y", (byte)0);
			
			if (sectionY < 0 || sectionY >= MAX_SECTIONS)
				continue;
			
			Section newSection = new Section();
			sections[sectionY] = newSection;
			
			ByteArrayTag blocksTag = NbtUtil.getChild(compound, "Blocks", ByteArrayTag.class);
			if (blocksTag != null)
			{
				for (int x=0; x<SECTION_WIDTH; x++)
				{
					for (int y=0; y<SECTION_HEIGHT; y++)
					{
						for (int z=0; z<SECTION_DEPTH; z++)
						{
							final int index = calcAnvilIndex(x, y, z);
							final int id = blocksTag.getValue()[index] & 0xFF;
							newSection.blockIds[x][y][z] = id;
						}
					}
				}
			}
			
			ByteArrayTag addTag = NbtUtil.getChild(compound, "Add", ByteArrayTag.class);
			if (addTag != null)
			{
				for (int x=0; x<SECTION_WIDTH; x++)
				{
					for (int y=0; y<SECTION_HEIGHT; y++)
					{
						for (int z=0; z<SECTION_DEPTH; z++)
						{
							final int index = calcAnvilIndex(x, y, z);
							final int addValue = addTag.getValue()[index];
							newSection.blockIds[x][y][z] = newSection.blockIds[x][y][z] + (addValue << 8);
						}
					}
				}
			}
			
			ByteArrayTag dataTag = NbtUtil.getChild(compound, "Data", ByteArrayTag.class);
			if (dataTag != null)
			{
				for (int x=0; x<SECTION_WIDTH; x++)
				{
					for (int y=0; y<SECTION_HEIGHT; y++)
					{
						for (int z=0; z<SECTION_DEPTH; z++)
						{
							final byte half = getAnvil4Bit(dataTag, x, y, z);
							newSection.blockData[x][y][z] = half;
						}
					}
				}
			}
			
			ByteArrayTag skylightTag = NbtUtil.getChild(compound, "SkyLight", ByteArrayTag.class);
			if (skylightTag != null)
			{
				for (int x=0; x<SECTION_WIDTH; x++)
				{
					for (int y=0; y<SECTION_HEIGHT; y++)
					{
						for (int z=0; z<SECTION_DEPTH; z++)
						{
							final byte half = getAnvil4Bit(skylightTag, x, y, z);
							newSection.skylight[x][y][z] = half;
						}
					}
				}
			}
			
			ByteArrayTag blocklightTag = NbtUtil.getChild(compound, "BlockLight", ByteArrayTag.class);
			if (blocklightTag != null)
			{
				for (int x=0; x<SECTION_WIDTH; x++)
				{
					for (int y=0; y<SECTION_HEIGHT; y++)
					{
						for (int z=0; z<SECTION_DEPTH; z++)
						{
							final byte half = getAnvil4Bit(blocklightTag, x, y, z);
							newSection.blocklight[x][y][z] = half;
						}
					}
				}
			}
		}
		
		// Parse "Biomes" data (16x16)
		ByteArrayTag biomeDataTag = NbtUtil.getChild(level, "Biomes", ByteArrayTag.class);
		if (biomeDataTag != null)
		{
			biomes = new byte[SECTION_WIDTH][SECTION_DEPTH];
			
			for (int x=0; x<SECTION_WIDTH; x++)
			{
				for (int z=0; z<SECTION_DEPTH; z++)
				{
					final int index = x * SECTION_WIDTH + z;
					biomes[x][z] = biomeDataTag.getValue()[index];
				}
			}
		}
	}
	
	private void parseMcRegionData(CompoundTag level)
	{
		// McRegion chunks are only 128 high, so just create the lower half of the sections
		for (int i=0; i<8; i++)
		{
			sections[i] = new Section();
		}
		
		ByteArrayTag blocks = NbtUtil.getChild(level, "Blocks", ByteArrayTag.class);
		if (blocks != null)
		{
			for (int x=0; x<WIDTH; x++)
			{
				for (int y=0; y<MC_REGION_HEIGHT; y++)
				{
					for (int z=0; z<DEPTH; z++)
					{
						final int index = calcIndex(x, y, z);
						final byte blockId = blocks.getValue()[index];
						setBlockId(x, y, z, blockId);
					}
				}
			}
		}
		
		ByteArrayTag dataTag = NbtUtil.getChild(level, "Data", ByteArrayTag.class);
		if (dataTag != null)
		{
			for (int x=0; x<WIDTH; x++)
			{
				for (int y=0; y<MC_REGION_HEIGHT; y++)
				{
					for (int z=0; z<DEPTH; z++)
					{
						final byte half = get4Bit(dataTag, x, y, z);
						setBlockData(x, y, z, half);
					}
				}
			}
		}
		
		ByteArrayTag skylightTag = NbtUtil.getChild(level, "SkyLight", ByteArrayTag.class);
		if (skylightTag != null)
		{
			for (int x=0; x<WIDTH; x++)
			{
				for (int y=0; y<MC_REGION_HEIGHT; y++)
				{
					for (int z=0; z<DEPTH; z++)
					{
						final byte half = get4Bit(skylightTag, x, y, z);
						setSkyLight(x, y, z, half);
					}
				}
			}
		}
		
		ByteArrayTag blockLightTag = NbtUtil.getChild(level, "BlockLight", ByteArrayTag.class);
		if (blockLightTag != null)
		{
			for (int x=0; x<WIDTH; x++)
			{
				for (int y=0; y<MC_REGION_HEIGHT; y++)
				{
					for (int z=0; z<DEPTH; z++)
					{
						final byte half = get4Bit(blockLightTag, x, y, z);
						setBlockLight(x, y, z, half);
					}
				}
			}
		}
	}
	
	private static final int calcIndex(final int x, final int y, final int z)
	{
		// y + ( z * ChunkSizeY(=128) + ( x * ChunkSizeY(=128) * ChunkSizeZ(=16) ) ) ];
		return y + (z * MC_REGION_HEIGHT) + (x * MC_REGION_HEIGHT * DEPTH);
	}
	
	private static final int calcAnvilIndex(final int x, final int y, final int z)
	{
		// Note that the old format is XZY ((x * 16 + z) * 128 + y)
		// and the new format is       YZX ((y * 16 + z) * 16 + x)
		
		return x + (z * SECTION_HEIGHT) + (y * SECTION_HEIGHT * SECTION_DEPTH);
	}
	
	private static final int calc4BitIndex(final int x, final int y, final int z)
	{
		// Math.floor is bloody slow!
		// Since calcIndex should always be +ive, we can just cast to int and get the same result 
		return (int)(calcIndex(x, y, z) / 2);
	}
	
	private static final int calcAnvil4BitIndex(final int x, final int y, final int z)
	{
		// Math.floor is bloody slow!
		// Since calcIndex should always be +ive, we can just cast to int and get the same result 
		return (int)(calcAnvilIndex(x, y, z) / 2);
	}
	
	private static byte getAnvil4Bit(ByteArrayTag tag, final int x, final int y, final int z)
	{
		final int index = calcAnvil4BitIndex(x, y, z);
		if (index == 2048)
			System.out.println();;
		
		final int doublet = tag.getValue()[index];
		
		// Upper or lower half?
		final boolean isUpper = (x % 2 == 1);
		
		byte half;
		if (isUpper)
		{
			half = (byte)((doublet >> 4) & 0xF);
		}
		else
		{
			half = (byte)(doublet & 0xF);
		}
		
		return half;
	}
	
	private static byte get4Bit(ByteArrayTag tag, final int x, final int y, final int z)
	{
		final int index = calc4BitIndex(x, y, z);
		final int doublet = tag.getValue()[index];
		
		// Upper or lower half?
		final boolean isUpper = (y % 2 == 1);
		
		byte half;
		if (isUpper)
		{
			half = (byte)((doublet >> 4) & 0xF);
		}
		else
		{
			half = (byte)(doublet & 0xF);
		}
		
		return half;
	}
	
	public int getBlockId(final int x, final int y, final int z)
	{
		final int sectionY = y / MAX_SECTIONS;
		final int localY = y % SECTION_HEIGHT;
		
		Section s = sections[sectionY];
		if (s != null)
			return s.blockIds[x][localY][z];
		else
			return BlockIds.AIR;
	}
	
	public void setBlockId(final int x, final int y, final int z, final int blockId)
	{
		final int sectionY = y / MAX_SECTIONS;
		final int localY = y % SECTION_HEIGHT;
		
		Section s = sections[sectionY];
		if (s == null)
		{
			s = new Section();
			sections[sectionY] = s;
		}
		
		s.blockIds[x][localY][z] = blockId;
	}
	
	private void setBlockData(final int x, final int y, final int z, final byte val)
	{
		final int sectionY = y / MAX_SECTIONS;
		final int localY = y % SECTION_HEIGHT;
		
		Section s = sections[sectionY];
		if (s == null)
		{
			s = new Section();
			sections[sectionY] = s;
		}
		
		s.blockData[x][localY][z] = val;
	}
	
	public int getBlockData(final int x, final int y, final int z)
	{
		final int sectionY = y / MAX_SECTIONS;
		final int localY = y % SECTION_HEIGHT;
		
		Section s = sections[sectionY];
		if (s != null && x >= 0 && x <= 15 && z >= 0 && z <= 15)  //TODO:  Fix this (workaround for painting and stair problems)
			return s.blockData[x][localY][z];
		else
			return 0;
	}
	
	public void setSkyLight(final int x, final int y, final int z, final byte val)
	{
		final int sectionY = y / MAX_SECTIONS;
		final int localY = y % SECTION_HEIGHT;
		
		Section s = sections[sectionY];
		if (s == null)
		{
			s = new Section();
			sections[sectionY] = s;
		}
		
		s.skylight[x][localY][z] = val;
	}
	
	public byte getSkyLight(final int x, final int y, final int z)
	{
		final int sectionY = y / MAX_SECTIONS;
		final int localY = y % SECTION_HEIGHT;
		
		Section s = sections[sectionY];
		if (s != null && x >= 0 && localY >= 0 && z >= 0)  //TODO: Fix this (workaround for painting and stair problems)
			return s.skylight[x][localY][z];
		else
			return MAX_LIGHT-1;
	}
	
	private void setBlockLight(final int x, final int y, final int z, final byte val)
	{
		final int sectionY = y / MAX_SECTIONS;
		final int localY = y % SECTION_HEIGHT;
		
		Section s = sections[sectionY];
		if (s == null)
		{
			s = new Section();
			sections[sectionY] = s;
		}
		
		s.blocklight[x][localY][z] = val;
	}
	
	public byte getBlockLight(final int x, final int y, final int z)
	{
		final int sectionY = y / MAX_SECTIONS;
		final int localY = y % SECTION_HEIGHT;
		
		Section s = sections[sectionY];
		if (s != null && x >= 0 && localY >= 0 && z >= 0)  //TODO: Fix this (workaround for painting and stair problems)
			return s.blocklight[x][localY][z];
		else
			return 0;
	}
	
	public int getBlockIdClamped(final int x, final int y, final int z, final int defaultId)
	{
		if (x < 0 || x >= WIDTH)
			return defaultId;
		if (y < 0 || y >= HEIGHT)
			return defaultId;
		if (z < 0 || z >= DEPTH)
			return defaultId;
		
		return getBlockId(x, y, z);
	}
	
	public int getBlockX() { return blockX; }
	public int getBlockY() { return blockY; }
	public int getBlockZ() { return blockZ; }
	
	public ChunkCoord getChunkCoord() { return new ChunkCoord(blockX, blockZ); }
	
	public long getMemorySize()
	{
		int blockIdTotal = 0;
		int skyLightTotal = 0;
		int blockLightTotal = 0;
		int blockDataTotal = 0;
		for (Section s : sections)
		{
			if (s != null)
			{
				blockIdTotal += s.blockIds.length * s.blockIds[0].length * s.blockIds[0][0].length;
				skyLightTotal += s.skylight.length * s.skylight[0].length * s.skylight[0][0].length;
				blockLightTotal += s.blocklight.length * s.blocklight[0].length * s.blocklight[0][0].length;
				blockDataTotal += s.blockData.length * s.blockData[0].length * s.blockData[0][0].length;
			}
		}
		
		return blockIdTotal + blockDataTotal + skyLightTotal + blockLightTotal;
	}

	public ArrayList<RawSign> getSigns()
	{
		return new ArrayList<RawSign>(signs);
	}
	
	public ArrayList<TileEntity> getFlowerPots()
	{
		return new ArrayList<TileEntity>(flowerPots);
	}
	
	public ArrayList<TileEntity> getPaintings()
	{
		return new ArrayList<TileEntity>(paintings);
	}
	
	public ArrayList<TileEntity> getSkulls()
	{
		return new ArrayList<TileEntity>(skulls);
	}
	
	public ArrayList<TileEntity> getBeacons()
	{
		return new ArrayList<TileEntity>(beacons);
	}
	
	public ArrayList<TileEntity> getBanners()
	{
		return new ArrayList<TileEntity>(banners);
	}
	
	public ArrayList<TileEntity> getItemFrames()
	{
		return new ArrayList<TileEntity>(itemFrames);
	}

	public byte[] calculateHash(MessageDigest hashAlgorithm)
	{
		hashAlgorithm.reset();
		
		for (Section s : sections)
		{
			if (s != null)
			{
				update(hashAlgorithm, s.blockIds);
				update(hashAlgorithm, s.blockData);
				update(hashAlgorithm, s.skylight);
				update(hashAlgorithm, s.blocklight);
			}
			else
			{
				byte[][][] dummy = new byte[1][1][1];
				update(hashAlgorithm, dummy);
			}
		}
		
		for (RawSign sign : signs)
		{
			hashAlgorithm.update(Integer.toString(sign.x).getBytes());
			hashAlgorithm.update(Integer.toString(sign.y).getBytes());
			hashAlgorithm.update(Integer.toString(sign.z).getBytes());
			hashAlgorithm.update(sign.text1.getBytes());
			hashAlgorithm.update(sign.text2.getBytes());
			hashAlgorithm.update(sign.text3.getBytes());
			hashAlgorithm.update(sign.text4.getBytes());
		}
		
		return hashAlgorithm.digest();
	}
	
	private static void update(MessageDigest hashAlgorithm, int[][][] data)
	{
		for (int x=0; x<data.length; x++)
		{
			for (int y=0; y<data[0].length; y++)
			{
				for (int z=0; y<data[0][0].length; y++)
				{
					final int val = data[x][y][z];
					
					hashAlgorithm.update((byte)((val)       & 0xFF));
					hashAlgorithm.update((byte)((val >>  8) & 0xFF));
					hashAlgorithm.update((byte)((val >> 16) & 0xFF));
					hashAlgorithm.update((byte)((val >> 24) & 0xFF));
				}
			}
		}
	}
	
	private static void update(MessageDigest hashAlgorithm, byte[][][] data)
	{
		for (int x=0; x<data.length; x++)
		{
			for (int y=0; y<data[0].length; y++)
			{
				hashAlgorithm.update(data[x][y]);
			}
		}
	}
	
	public int getBiomeId(final int x, final int y, final int z)
	{
		if(biomes != null)
			return biomes[x][z];
		else
			return BiomeIds.UNKNOWN;
	}
	
	private static class Section
	{
		public int[][][] blockIds;
		public byte[][][] blockData;
		
		public byte[][][] skylight;
		public byte[][][] blocklight;
		
		public Section()
		{
			blockIds = new int[SECTION_WIDTH][SECTION_HEIGHT][SECTION_DEPTH];
			blockData = new byte[SECTION_WIDTH][SECTION_HEIGHT][SECTION_DEPTH];
			
			skylight = new byte[SECTION_WIDTH][SECTION_HEIGHT][SECTION_DEPTH];
			blocklight = new byte[SECTION_WIDTH][SECTION_HEIGHT][SECTION_DEPTH];
		}
	}

}
