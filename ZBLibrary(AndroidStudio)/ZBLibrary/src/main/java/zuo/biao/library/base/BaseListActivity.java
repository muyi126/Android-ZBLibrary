/*Copyright ©2015 TommyLemon(https://github.com/TommyLemon)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.*/

package zuo.biao.library.base;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import zuo.biao.library.R;
import zuo.biao.library.interfaces.AdapterCallBack;
import zuo.biao.library.interfaces.OnCacheCallBack;
import zuo.biao.library.interfaces.OnStopLoadListener;
import zuo.biao.library.manager.HttpManager;
import zuo.biao.library.manager.CacheManager;
import zuo.biao.library.util.Log;
import zuo.biao.library.util.StringUtil;
import android.widget.AbsListView;
import android.widget.BaseAdapter;

/**基础列表Activity
 * @author Lemon
 * @param <T> 数据模型(model/JavaBean)类
 * @param <LV> AbsListView的子类（ListView,GridView等）
 * @param <BA> 管理LV的Adapter
 * @see #lvBaseList
 * @see #initCache
 * @see #initView
 * @see #getListAsync
 * @see #onRefresh
 * @use extends BaseListActivity 并在子类onCreate中调用onRefresh(...), 具体参考.DemoListActivity
 * *缓存使用：在initData前调用initCache(...), 具体参考 .DemoListActivity(onCreate方法内)
 */
public abstract class BaseListActivity<T, LV extends AbsListView, BA extends BaseAdapter> extends BaseActivity {
	private static final String TAG = "BaseListActivity";

	private OnStopLoadListener onStopLoadListener;
	/**设置停止加载监听
	 * @param onStopLoadListener
	 */
	protected void setOnStopLoadListener(OnStopLoadListener onStopLoadListener) {
		this.onStopLoadListener = onStopLoadListener;
	}


	private OnCacheCallBack<T> onCacheCallBack;
	/**初始化缓存
	 * @warn 在initData前使用才有效
	 * @param onCacheCallBack
	 */
	protected void initCache(OnCacheCallBack<T> onCacheCallBack) {
		this.onCacheCallBack = onCacheCallBack;
	}




	// UI显示区(操作UI，但不存在数据获取或处理代码，也不存在事件监听代码)<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

	/**
	 * 显示列表的ListView
	 * @warn 只使用lvBaseList为显示列表数据的AbsListView(ListView,GridView等)，不要在子类中改变它
	 */
	protected LV lvBaseList;
	/**
	 * 如果在子类中调用(即super.initView());则view必须含有initView中初始化用到的id且id对应的View的类型全部相同；
	 * 否则必须在子类initView中重写这个类中initView内的代码(所有id替换成可用id)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void initView() {// 必须调用

		lvBaseList = (LV) findViewById(R.id.lvBaseList);
	}

	/**显示列表（已在UI线程中），一般需求建议直接调用setList(List<T> l, AdapterCallBack<BA> callBack)
	 * @param list
	 */
	public abstract void setList(List<T> list);

	/**
	 * 管理LV的Item的Adapter
	 */
	protected BA adapter;
	/**显示列表，这个方法符合一般需求，建议使用。
	 * @param l this.list = l;
	 * @param callBack createAdapter可以直接用这个类的list，refreshAdapter无需判断adapter
	 * @use 在setList(List<T> list)方法内调用
	 */
	public void setList(List<T> l, AdapterCallBack<BA> callBack) {
		this.list = l;
		if (list == null || list.isEmpty()) {
			Log.e(TAG, "setList list == null || list.isEmpty() >> setAdapter(null); return;");
			setAdapter(null);
			return;
		}

		if (adapter == null) {
			setAdapter(callBack.createAdapter());
		} else {
			callBack.refreshAdapter();
		}
	}

	/**设置adapter
	 * @param adapter
	 */
	public void setAdapter(BA adapter) {
		this.adapter = adapter;
		lvBaseList.setAdapter(adapter);
	}


	// UI显示区(操作UI，但不存在数据获取或处理代码，也不存在事件监听代码)>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>










	// data数据区(存在数据获取或处理代码，但不存在事件监听代码)<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

	protected boolean isToLoadCache;
	protected boolean isToSaveCache;
	@Override
	public void initData() {// 必须调用

		isToSaveCache = onCacheCallBack != null && onCacheCallBack.getCacheClass() != null;
		isToLoadCache = isToSaveCache && StringUtil.isNotEmpty(onCacheCallBack.getCacheGroup(), true);
	}

	/**
	 * 获取列表，在非UI线程中
	 * @must 获取成功后调用onLoadSucceed
	 * @param pageNum
	 */
	public abstract void getListAsync(int pageNum);


	public void loadData(int pageNum) {
		loadData(pageNum, isToLoadCache);
	}
	/**
	 * 正在加载
	 */
	protected boolean isLoading = false;
	//	/**线程不同步导致获取的值不可靠
	//	 * 正在加载缓存
	//	 */
	//	protected boolean isLoadingCache = false;
	/**
	 * 还有更多可加载数据
	 */
	protected boolean isHaveMore = true;
	/**
	 * 加载页码，每页对应一定数量的数据
	 */
	protected int pageNum;
	protected int loadCacheStart;
	/**加载数据，用getListAsync方法发请求获取数据
	 * @param pageNum_
	 * @param isToLoadCache
	 */
	public void loadData(int pageNum_, final boolean isToLoadCache) {
		if (isLoading) {
			Log.e(TAG, "loadData  isLoading >> return;");
			return;
		}
		isLoading = true;

		if (pageNum_ <= HttpManager.PAGE_NUM_0) {
			pageNum_ = HttpManager.PAGE_NUM_0;
			isHaveMore = true;
			loadCacheStart = 0;//使用则可像网络正常情况下的重载，不使用则在网络异常情况下不重载（导致重载后加载数据下移）
		} else {
			if (isHaveMore == false) {
				stopLoadData();
				return;
			}
			loadCacheStart = list == null ? 0 : list.size();
		}
		this.pageNum = pageNum_;

		runThread(TAG + "loadData", new Runnable() {

			@Override
			public void run() {
				//从缓存获取数据
				List<T> newList = isToLoadCache == false ? null : CacheManager.getInstance().getList(
						onCacheCallBack.getCacheClass(), onCacheCallBack.getCacheGroup(), loadCacheStart);
				if (newList == null || newList.isEmpty()) {
					getListAsync(pageNum);
					return;
				}

				onLoadSucceed(newList, true);
				if (pageNum <= HttpManager.PAGE_NUM_0) {
					isLoading = false;//stopLoadeData在其它线程isLoading = false;后这个线程里还是true
					loadData(pageNum, false);
				}
			}
		});
	}

	/**停止加载数据
	 * isCache = false;
	 */
	public synchronized void stopLoadData() {
		stopLoadData(false);
	}
	/**停止加载数据
	 * @param isCache
	 */
	public synchronized void stopLoadData(boolean isCache) {
		isLoading = false;
		dismissProgressDialog();

		if (isCache) {
			Log.d(TAG, "stopLoadData  isCache >> return;");
			return;
		}

		if (onStopLoadListener == null) {
			Log.e(TAG, "stopLoadData  onStopLoadListener == null >> return;");
			return;
		}
		if (pageNum <= HttpManager.PAGE_NUM_0) {
			onStopLoadListener.onStopRefresh();
		} else {
			onStopLoadListener.onStopLoadMore(isHaveMore);
		}
	}



	/**
	 * 数据列表
	 */
	protected List<T> list;
	/**
	 * 新数据列表
	 */
	protected List<T> newList = null;
	/**处理列表
	 * @param newList_ 新数据列表
	 * @param isCache 
	 * @return
	 */
	public synchronized void handleList(List<T> newList_, boolean isCache) {
		this.newList = newList_;
		if (newList == null) {
			newList = new ArrayList<>();
		}

		if (pageNum <= HttpManager.PAGE_NUM_0) {
			saveCacheStart = 0;
			list = newList;
			if (isCache == false && list != null && list.size() > 0) {
				isToLoadCache = false;
			}
		} else {
			saveCacheStart = list == null ? 0 : list.size();
			if (newList.size() <= 0) {
				isHaveMore = false;
			} else {
				if (list == null) {
					list = new ArrayList<>();
				}
				list.addAll(newList);
			}
		}
	}




	private int saveCacheStart;
	/**保存缓存
	 */
	public synchronized void saveCache() {
		if (onCacheCallBack == null) {
			Log.e(TAG, "saveCache  onCacheCallBack == null >> return;");
			return;
		}

		LinkedHashMap<String, T> map = new LinkedHashMap<>();
		for (T data : newList) {
			if (data != null) {
				map.put(onCacheCallBack.getCacheId(data), data);//map.put(null, data);不会崩溃
			}
		}

		CacheManager.getInstance().saveList(onCacheCallBack.getCacheClass(), onCacheCallBack.getCacheGroup()
				, map, saveCacheStart, newList.size());
	}


	/**加载成功
	 * isCache = false;
	 * @param newList
	 */
	public synchronized void onLoadSucceed(final List<T> newList) {
		onLoadSucceed(newList, false);
	}
	/**加载成功
	 * @param newList
	 * @param isCache newList是否为缓存
	 */
	public synchronized void onLoadSucceed(final List<T> newList, final boolean isCache) {
		runThread(TAG + "onLoadSucceed", new Runnable() {
			@Override
			public void run() {

				handleList(newList, isCache);

				runUiThread(new Runnable() {

					@Override
					public void run() {
						setList(list);
						stopLoadData(isCache);
					}
				});

				if (isToSaveCache && isCache == false) {
					saveCache();
				}
			}
		});
	}

	/**加载失败
	 * @param e
	 */
	public synchronized void onLoadFailed(Exception e) {
		Log.e(TAG, "onLoadFailed e = " + e);
		stopLoadData();
		showShortToast(R.string.get_failed);
	}


	// data数据区(存在数据获取或处理代码，但不存在事件监听代码)>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>










	// listener事件监听区(只要存在事件监听代码就是)<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

	@Override
	public void initListener() {

	}


	/**刷新（从头加载）
	 * @must 在子类onCreate中调用，建议放在最后
	 */
	public void onRefresh() {
		loadData(HttpManager.PAGE_NUM_0);
	}
	/**加载更多
	 */
	public void onLoadMore() {
		loadData(pageNum + 1);
	}


	// 系统自带监听方法<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<


	// 类相关监听<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

	@Override
	public void onDestroy() {
		stopLoadData();

		super.onDestroy();
		isLoading = false;
		isHaveMore = true;

		lvBaseList = null;

		list = null;
		newList = null;

		onStopLoadListener = null;
		onCacheCallBack = null;
	}

	// 类相关监听>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

	// 系统自带监听方法>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>


	// listener事件监听区(只要存在事件监听代码就是)>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>



	// 内部类,尽量少用<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

	// 内部类,尽量少用>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

}