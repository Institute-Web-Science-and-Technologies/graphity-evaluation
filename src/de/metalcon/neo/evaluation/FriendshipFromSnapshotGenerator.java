package de.metalcon.neo.evaluation;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;

import org.neo4j.kernel.AbstractGraphDatabase;
import org.neo4j.kernel.impl.batchinsert.BatchInserter;
import org.neo4j.kernel.impl.batchinsert.BatchInserterImpl;

import de.metalcon.neo.evaluation.neo.NeoUtils;
import de.metalcon.neo.evaluation.neo.Relations;
import de.metalcon.neo.evaluation.utils.Configs;
import de.metalcon.neo.evaluation.utils.CopyDirectory;
import de.metalcon.neo.evaluation.utils.H;
import de.metalcon.neo.evaluation.utils.StopWatch;

/**
 * Reads the given snapshots files containing relation adds and updates and
 * fills a Neo database with the corresponding nodes.
 * 
 * @author Jonas Kunze Rene Pickhardt
 * 
 */
public class FriendshipFromSnapshotGenerator {
	protected final String addSnapshotPath, updateSnapshotPath;
	protected final String StarDBPathPrefix;
	protected final String FFDBPathPrefix;
	protected final long[] starSnapshotTimestamps;

	public FriendshipFromSnapshotGenerator(String DBPathPrefix,
			long[] starSnapshotTimestamps, String addSnapshotPath,
			String updateSnapshotPath) {
		this.StarDBPathPrefix = DBPathPrefix;
		this.addSnapshotPath = addSnapshotPath;
		this.updateSnapshotPath = updateSnapshotPath;
		this.starSnapshotTimestamps = starSnapshotTimestamps;

		this.FFDBPathPrefix = Configs.get().FFDBDirPrefix;
	}

	public void run() throws InterruptedException {
		for (long ts : starSnapshotTimestamps) {
			AddSnapshotRunner addRunner = new AddSnapshotRunner(
					StarDBPathPrefix + ts, ts);
			addRunner.start();

			StopWatch watch = new StopWatch();
			while (addRunner.isAlive()) {
				Thread.sleep(1000);
				watch.updateRate(addRunner.addedRelations());
				H.log(ts + " Added " + addRunner.addedRelations()
						+ " relations so far: " + watch.getRate(10) + "ps : "
						+ watch.getTotalRate() + "ps total");

			}
			H.strongLog("FriendshiptGenerator " + ts + " DONE adding "
					+ addRunner.addedRelations() + " relations: "
					+ watch.getRate(10) + "ps : " + watch.getTotalRate()
					+ "ps total");
		}
	}

	private class AddSnapshotRunner extends Thread {
		int addedRelations = 0;
		final BatchInserter inserter;
		final long starSnapshotTimestamp;

		public AddSnapshotRunner(String path, long starSnapshotTimestamp) {
			AbstractGraphDatabase db = NeoUtils.getAbstractGraphDatabase(path);
			NeoUtils.ShutdownDB(db, path);
			this.inserter = new BatchInserterImpl(path,
					BatchInserterImpl.loadProperties(path
							+ "/neostore.propertystore.db"));
			this.starSnapshotTimestamp = starSnapshotTimestamp;
		}

		public int addedRelations() {
			return addedRelations;
		}

		@Override
		public void run() {
			/**
			 * add friendships copy db to FlatFiles db insert updates copy db to
			 * graphity db
			 */

			InsertFriendships();
			new CopyDirectory(StarDBPathPrefix + starSnapshotTimestamp,
					FFDBPathPrefix + starSnapshotTimestamp);
			new CopyDirectory(StarDBPathPrefix + starSnapshotTimestamp,
					Configs.get().CleanFriendDBPrefix + starSnapshotTimestamp);
		}

		private void InsertFriendships() {
			try {
				HashMap<Long, String> allIDs = new HashMap<Long, String>();

				BufferedReader in;
				if (!Configs.get().AddAllEntitiesToAllSnapshots) {
					in = H.openReadFile(Configs.get().SnapshotIDListPrefix
							+ starSnapshotTimestamp);
				} else {
					in = H.openReadFile(Configs.get().SnapshotIDListPrefix
							+ "1296514800");
				}

				String strLine;
				String[] values;
				int lineCount = 0;
				while ((strLine = in.readLine()) != null) {
					values = strLine.split("\t");
					allIDs.put(Long.valueOf(values[0]), values[1]);
					if (lineCount++ % 500000 == 0) {
						H.pln("Reading all IDs: " + lineCount + " done so far");
					}
				}
				in.close();

				boolean[] inserted = new boolean[2300000];

				in = H.openReadFile(addSnapshotPath + starSnapshotTimestamp);
				H.pln(addSnapshotPath + starSnapshotTimestamp);

				while ((strLine = in.readLine()) != null) {
					values = strLine.split("\t");
					if (values.length != 2) {
						continue;
					}
					int fromKey = Integer.valueOf(values[0]);
					int toKey = Integer.valueOf(values[1]);

					if (fromKey == toKey) {
						continue;
					}

					if (!inserted[fromKey]) {
						HashMap<String, Object> properties = new HashMap<String, Object>();
						if (!allIDs.containsKey((long) fromKey)) {
							continue;
						}
						properties.put("title", allIDs.get((long) fromKey));
						inserter.createNode(fromKey, properties);
						inserted[fromKey] = true;
					}

					if (!inserted[toKey]) {
						HashMap<String, Object> properties = new HashMap<String, Object>();
						if (!allIDs.containsKey((long) toKey)) {
							continue;
						}
						properties.put("title", allIDs.get((long) toKey));
						inserter.createNode(toKey, properties);
						inserted[toKey] = true;
					}
					inserter.createRelationship((long) fromKey, (long) toKey,
							Relations.FOLLOWS, null);
					addedRelations++;
				}
				inserter.shutdown();
			} catch (NumberFormatException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}
