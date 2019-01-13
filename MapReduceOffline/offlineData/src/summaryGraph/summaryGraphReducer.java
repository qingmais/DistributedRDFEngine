package summaryGraph;

import hbase.myHbase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import mapreduce.oReport;
import mapreduce.offlineDriver;

import org.apache.hadoop.io.BooleanWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reducer;
import org.apache.hadoop.mapred.Reporter;

import com.sun.org.apache.bcel.internal.generic.NEW;

public class summaryGraphReducer extends MapReduceBase
implements Reducer<Text, mEntry, Text, Text>
{
	boolean testMode = true; 
	boolean useHM = false;
	HashSet<Integer> hsI = new HashSet<Integer>();
	private Text tVal = new Text();
	private static HashMap<Integer, Integer> hmInsert = new HashMap<Integer, Integer>();
	ArrayList<mEntry> eList = new ArrayList<mEntry>();
	public void reduce(Text _key, Iterator values,
			OutputCollector output, Reporter reporter)
	throws IOException 
	{
		{
			if(_ii < 3) reporter.setStatus(_key.toString());
//			System.out.print(_key.toString() + "\t");
//			if(_ii % 100 == 0)
//				System.out.print("\n");
			if(testMode) hsI.clear();
		}
		eList.clear();
		if(useHM){
			hmInsert.clear();
		}
		int _count = 0;
		while (values.hasNext()) 
		{
			mEntry _MEntry = (mEntry)values.next();
			if(!testMode)
			{
				Insert(eList, _MEntry);
			}
			else 
			{
				_count ++;
//				hsI.add(_MEntry.getId());
			}
		}
		if(testMode){
			if(_count > 100000){
				output.collect(new Text(_key.toString()), new Text(_count + ""));
			}
			if(_ii < 3)
			{
				output.collect(new Text(_key.toString()), new Text(_count + ""));
			}
		}
		if(!testMode)
		{
			for(int i = 0; i < eList.size(); i ++)
			{
				tVal.set(eList.get(i).toString());
				output.collect(_key, tVal);
			}
			for(int i = 1; i < eList.size(); i ++){
				if(eList.get(i - 1).getId() >= eList.get(i).getId())
					reporter.incrCounter(oReport.WrongSort, 1);
			}
		}
		{//load the summary graph that made up of V2E and E2E
//			loadLinkedSumGraph(_key, eList, output, reporter);
			if(! testMode)
			{
				forYago_SumEdge(_key, eList, output, reporter);
			}
		}
		{	
			_ii ++;
		}
	}
	private int _ii = 0;
	private void forYago_SumEdge(Text _key, ArrayList<mEntry> _e_list,
			OutputCollector output, Reporter reporter) throws IOException
	{
		// s_subID_level , o_objID_level
		String[] _sp = _key.toString().split("_");
		int _id = Integer.parseInt(_sp[1]);
		int _level = Integer.parseInt(_sp[2]);
		if(_sp[0].equals("s"))
		{
			myHbase.loadOutEdges(_id, _level, _e_list);
		}
		else 
		if(_sp[0].equals("o"))
		{
			myHbase.loadInEdges(_id, _level, _e_list);
		}
		else
		if(_sp[0].equals("l"))
		{
			myHbase.loadLiteralEdge(_id, _level, _e_list);
		}
		incReporter(_sp[0], _e_list.size(), _level, reporter);
	}
	private void loadLinkedSumGraph(Text _key, ArrayList<mEntry> _e_list,
			OutputCollector output, Reporter reporter) throws IOException
	{
		// s_subID_level , o_objID_level
		String[] _sp = _key.toString().split("_");
		int _id = Integer.parseInt(_sp[1]);
		int _level = Integer.parseInt(_sp[2]);
		if(_sp[0].equals("s"))
		{
			int _foi = _e_list.get(0).getId();
			myHbase.VFirstOutE(_id, _foi, _level);
			String _cur_edge = offlineDriver.encodeEdge(_id, _foi);
			String _next_edge = null;
			for(int i = 1; i < _e_list.size(); i ++)
			{
				_next_edge = offlineDriver.encodeEdge(_id, _e_list.get(i).getId());
				myHbase.ENextOutE(_cur_edge, _next_edge, _level);
				_cur_edge = _next_edge;
			}
			myHbase.setEsigList(_id, _e_list, _level);
		}
		else 
		if(_sp[0].equals("o"))
		{
			int _fii = _e_list.get(0).getId();
			myHbase.VFirstInE(_id, _fii, _level);
			String _cur_edge = offlineDriver.encodeEdge(_fii, _id);
			String _next_edge = null;
			for(int i = 1; i < _e_list.size(); i ++)
			{
				_next_edge = offlineDriver.encodeEdge(_e_list.get(i).getId(), _id);
				myHbase.ENextInE(_cur_edge, _next_edge, _level);
				_cur_edge = _next_edge;
			}
		}
		incReporter(_sp[0], _e_list.size(), _level, reporter);
	}
	oReport[] _foe = new oReport[]{oReport.foeNum, oReport.foeNum1, oReport.foeNum2, oReport.foeNum3};
	oReport[] _fie = new oReport[]{oReport.fieNum, oReport.fieNum1, oReport.fieNum2, oReport.fieNum3};
	oReport[] _noe = new oReport[]{oReport.noeNum, oReport.noeNum1, oReport.noeNum2, oReport.noeNum3};
	oReport[] _nie = new oReport[]{oReport.nieNum, oReport.nieNum1, oReport.nieNum2, oReport.nieNum3};
	oReport[] _esig = new oReport[]{oReport.eSigNum, oReport.eSigNum1, oReport.eSigNum2, oReport.eSigNum3};
	private void incReporter(String _type, int nNext, int _level, Reporter _r)
	{
		{
			if(_level > _foe.length) return;
		}
		if(_type.equals("s"))
		{
			_r.incrCounter(_foe[_level], 1);
			_r.incrCounter(_noe[_level], nNext - 1);
			_r.incrCounter(_esig[_level], nNext);
		}
		else
		{
			_r.incrCounter(_fie[_level], 1);
			_r.incrCounter(_nie[_level], nNext - 1);
		}
	}
	public void Insert(ArrayList<mEntry> _e_list, mEntry _en)
	{
		if(useHM){
			Integer _I = hmInsert.get(_en.getId());
			if(_I == null){
				_e_list.add(_en.clone());
				hmInsert.put(_en.getId(), _e_list.size() - 1);
				return;
			}
			_e_list.get(_I.intValue()).getSig().or(_en.getSig());
		}else{
			
		}
	}
	public void order_Insert(ArrayList<mEntry> _e_list, mEntry _en)
	{
		int _begin = 0, _end = _e_list.size();
		int _middle = 0;
		while(true){
			
		}
	}
	public void configure(JobConf _job)
	{
	}
}